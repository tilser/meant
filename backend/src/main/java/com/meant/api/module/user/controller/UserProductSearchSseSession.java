package com.meant.api.module.user.controller;

import com.meant.api.module.user.controller.response.UserProductSearchStreamEventResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
final class UserProductSearchSseSession {

    private static final long DRAIN_POLL_MILLISECONDS = 250L;

    private final SseEmitter emitter;
    private final ArrayBlockingQueue<UserProductSearchStreamEventResponse> events;
    private final UUID userId;
    private final UUID merchantId;
    private final UserStreamEventWriter userStreamEventWriter;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean cleanupStarted = new AtomicBoolean();

    private CompletableFuture<Void> searchFuture;
    private CompletableFuture<Void> drainFuture;

    UserProductSearchSseSession(
            SseEmitter emitter,
            int queueCapacity,
            UUID userId,
            UUID merchantId,
            UserStreamEventWriter userStreamEventWriter
    ) {
        this.emitter = emitter;
        this.events = new ArrayBlockingQueue<>(queueCapacity);
        this.userId = userId;
        this.merchantId = merchantId;
        this.userStreamEventWriter = userStreamEventWriter;
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
                                    "Product search stream failed. userId={}, merchantId={}",
                                    userId,
                                    merchantId,
                                    exception
                            );
                            send(UserProductSearchStreamEventResponse.error(
                                    "Product search failed. Please try again."
                            ));
                        }
                        closed.set(true);
                    });
            drainFuture = CompletableFuture.runAsync(this::drain, executor);
        }
    }

    void send(UserProductSearchStreamEventResponse event) {
        if (cancelled.get()) {
            return;
        }
        if (events.offer(event)) {
            return;
        }
        if (!isTerminal(event)) {
            log.debug(
                    "Dropping product search stream event because the client queue is full. userId={}, eventType={}",
                    userId,
                    event.type()
            );
            return;
        }
        while (!events.offer(event)) {
            events.poll();
        }
    }

    private void drain() {
        try {
            while (!closed.get() || !events.isEmpty()) {
                UserProductSearchStreamEventResponse event = events.poll(
                        DRAIN_POLL_MILLISECONDS,
                        TimeUnit.MILLISECONDS
                );
                if (event != null) {
                    userStreamEventWriter.writeProductSearchEvent(emitter, event);
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

    private boolean isTerminal(UserProductSearchStreamEventResponse event) {
        return "done".equals(event.type()) || "error".equals(event.type());
    }
}
