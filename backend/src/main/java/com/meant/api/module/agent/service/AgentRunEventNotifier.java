package com.meant.api.module.agent.service;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Process-local wakeups for durable agent run events. Notifications are deliberately lossy: subscribers always replay
 * the database cursor, so a dropped or cross-instance signal only waits until the slow fallback poll.
 */
@Component
public class AgentRunEventNotifier {

    private final ConcurrentHashMap<UUID, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public Subscription subscribe(UUID runId, int queueCapacity) {
        if (runId == null) {
            throw new IllegalArgumentException("runId is required");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        Subscription subscription = new Subscription(this, runId, queueCapacity);
        subscriptions.compute(runId, (ignored, current) -> {
            Set<Subscription> next = current == null ? ConcurrentHashMap.newKeySet() : current;
            next.add(subscription);
            return next;
        });
        return subscription;
    }

    public void signalAfterCommit(UUID runId, long cursor) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    signal(runId, cursor);
                }
            });
            return;
        }
        signal(runId, cursor);
    }

    private void signal(UUID runId, long cursor) {
        Set<Subscription> current = subscriptions.get(runId);
        if (current == null) {
            return;
        }
        current.forEach(subscription -> subscription.signal(cursor));
    }

    private void unsubscribe(Subscription subscription) {
        subscriptions.computeIfPresent(subscription.runId, (ignored, current) -> {
            current.remove(subscription);
            return current.isEmpty() ? null : current;
        });
    }

    int subscriberCount(UUID runId) {
        Set<Subscription> current = subscriptions.get(runId);
        return current == null ? 0 : current.size();
    }

    public static final class Subscription implements AutoCloseable {

        private final AgentRunEventNotifier owner;
        private final UUID runId;
        private final ArrayBlockingQueue<Long> signals;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Subscription(AgentRunEventNotifier owner, UUID runId, int queueCapacity) {
            this.owner = owner;
            this.runId = runId;
            signals = new ArrayBlockingQueue<>(queueCapacity);
        }

        public OptionalLong awaitLatestCursor(Duration timeout) throws InterruptedException {
            if (closed.get()) {
                return OptionalLong.empty();
            }
            Long cursor = signals.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (cursor == null) {
                return OptionalLong.empty();
            }
            long latest = cursor;
            while ((cursor = signals.poll()) != null) {
                latest = Math.max(latest, cursor);
            }
            return OptionalLong.of(latest);
        }

        private synchronized void signal(long cursor) {
            if (closed.get()) {
                return;
            }
            while (!signals.offer(cursor)) {
                signals.poll();
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            synchronized (this) {
                signals.clear();
                signals.offer(Long.MIN_VALUE);
            }
            owner.unsubscribe(this);
        }
    }
}
