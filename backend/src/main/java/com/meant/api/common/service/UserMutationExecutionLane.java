package com.meant.api.common.service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Serializes user-scoped mutations inside one application instance.
 *
 * <p>The lease API lets callers bound only the time spent waiting for admission.
 * Once admitted, callers retain the lane until the mutation has a confirmed outcome.
 */
@Service
public class UserMutationExecutionLane {

    private final Map<UUID, Lane> lanes = new ConcurrentHashMap<>();

    public <T> T execute(UUID userId, Supplier<T> action) throws InterruptedException {
        Objects.requireNonNull(action, "action");
        try (Lease ignored = acquire(userId)) {
            return action.get();
        }
    }

    public void execute(UUID userId, Runnable action) throws InterruptedException {
        Objects.requireNonNull(action, "action");
        execute(userId, () -> {
            action.run();
            return null;
        });
    }

    private Lease acquire(UUID userId) throws InterruptedException {
        Lane lane = retain(userId);
        boolean acquired = false;
        try {
            lane.lock.lockInterruptibly();
            acquired = true;
            return new Lease(userId, lane);
        } finally {
            if (!acquired) {
                release(userId, lane);
            }
        }
    }

    public Lease tryAcquire(UUID userId, long waitNanos) throws InterruptedException, TimeoutException {
        if (waitNanos < 0) {
            throw new IllegalArgumentException("waitNanos must not be negative");
        }
        Lane lane = retain(userId);
        boolean acquired = false;
        try {
            acquired = lane.lock.tryLock(waitNanos, TimeUnit.NANOSECONDS);
            if (!acquired) {
                throw new TimeoutException("Timed out waiting for the user's mutation lane");
            }
            return new Lease(userId, lane);
        } finally {
            if (!acquired) {
                release(userId, lane);
            }
        }
    }

    private Lane retain(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return lanes.compute(userId, (ignored, current) -> {
            Lane retained = current == null ? new Lane() : current;
            retained.references += 1;
            return retained;
        });
    }

    private void release(UUID userId, Lane lane) {
        lanes.computeIfPresent(userId, (ignored, current) -> {
            if (current != lane) {
                return current;
            }
            current.references -= 1;
            return current.references == 0 ? null : current;
        });
    }

    public final class Lease implements AutoCloseable {

        private final UUID userId;
        private final Lane lane;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(UUID userId, Lane lane) {
            this.userId = userId;
            this.lane = lane;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                lane.lock.unlock();
                release(userId, lane);
            }
        }
    }

    private static final class Lane {

        private final ReentrantLock lock = new ReentrantLock(true);
        private int references;
    }
}
