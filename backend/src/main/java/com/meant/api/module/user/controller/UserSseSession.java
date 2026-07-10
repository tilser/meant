package com.meant.api.module.user.controller;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
final class UserSseSession<T> {

    private static final long DRAIN_POLL_MILLISECONDS = 250L;

    private final SseEmitter emitter;
    private final ArrayBlockingQueue<T> events;
    private final UUID userId;
    private final UUID merchantId;
    private final SseEventSender<T> eventSender;
    private final Predicate<T> terminalPredicate;
    private final Function<Throwable, T> errorEventFactory;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean cleanupStarted = new AtomicBoolean();
    private final Object eventAdmissionLock = new Object();

    private CompletableFuture<Void> searchFuture;
    private CompletableFuture<Void> drainFuture;
    private boolean terminalAccepted;

    UserSseSession(
            SseEmitter emitter,
            int queueCapacity,
            UUID userId,
            UUID merchantId,
            SseEventSender<T> eventSender,
            Predicate<T> terminalPredicate,
            Function<Throwable, T> errorEventFactory
    ) {
        this.emitter = emitter;
        this.events = new ArrayBlockingQueue<>(queueCapacity);
        this.userId = userId;
        this.merchantId = merchantId;
        this.eventSender = eventSender;
        this.terminalPredicate = terminalPredicate;
        this.errorEventFactory = errorEventFactory;
    }

    void start(Runnable searchTask) {
        emitter.onCompletion(this::cancel);
        emitter.onTimeout(() -> {
            cancel();
            emitter.complete();
        });
        emitter.onError(exception -> cancel());

        synchronized (this) {
            if (cancelled.get()) {
                closed.set(true);
                shutdown();
                return;
            }
            searchFuture = CompletableFuture.runAsync(searchTask, executor)
                    .whenComplete((ignored, exception) -> {
                        if (exception != null && !cancelled.get()) {
                            log.warn(
                                    "User stream failed. userId={}, merchantId={}, failureType={}",
                                    userId,
                                    merchantId,
                                    exception.getClass().getName()
                            );
                            send(errorEventFactory.apply(exception));
                        }
                        closed.set(true);
                    });
            drainFuture = CompletableFuture.runAsync(this::drain, executor);
        }
    }

    void send(T event) {
        synchronized (eventAdmissionLock) {
            boolean terminal = terminalPredicate.test(event);
            if (cancelled.get() || terminalAccepted) {
                return;
            }
            if (terminal) {
                terminalAccepted = true;
            }
            if (events.offer(event)) {
                return;
            }
            if (!terminal) {
                log.debug("Dropping user stream event because the client queue is full. userId={}", userId);
                return;
            }
            while (!events.offer(event)) {
                events.poll();
            }
        }
    }

    private void drain() {
        try {
            while (!closed.get() || !events.isEmpty()) {
                T event = events.poll(DRAIN_POLL_MILLISECONDS, TimeUnit.MILLISECONDS);
                if (event != null) {
                    eventSender.send(emitter, event);
                }
            }
            emitter.complete();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            completeWithError(exception);
        } catch (IOException | RuntimeException exception) {
            completeWithError(exception);
        } finally {
            cancel();
        }
    }

    private void completeWithError(Throwable exception) {
        if (cancelled.compareAndSet(false, true)) {
            try {
                emitter.completeWithError(exception);
            } finally {
                cleanup();
            }
            return;
        }
        cleanup();
    }

    private void cancel() {
        cancelled.compareAndSet(false, true);
        cleanup();
    }

    private void cleanup() {
        if (!cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        synchronized (this) {
            closed.set(true);
            if (searchFuture != null) {
                searchFuture.cancel(true);
            }
            if (drainFuture != null) {
                drainFuture.cancel(true);
            }
            shutdown();
        }
    }

    private void shutdown() {
        executor.shutdownNow();
    }

    @FunctionalInterface
    interface SseEventSender<T> {
        void send(SseEmitter emitter, T event) throws IOException;
    }
}
