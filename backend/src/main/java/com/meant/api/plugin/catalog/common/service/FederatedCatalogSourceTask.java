package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryRequest;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailure;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailureKind;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceOperation;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceResult;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One independently cancellable source execution with per-source event and deadline admission. */
final class FederatedCatalogSourceTask {

    private final CatalogDiscoverySource source;
    private final CatalogDiscoveryRequest request;
    private final FederatedCatalogEventDispatcher eventDispatcher;
    private final BlockingQueue<Completion> completions;
    private final AtomicBoolean terminal = new AtomicBoolean();

    private volatile Future<?> future;
    private volatile ScheduledFuture<?> timeoutFuture;

    FederatedCatalogSourceTask(
            CatalogDiscoverySource source,
            CatalogDiscoveryRequest request,
            FederatedCatalogEventDispatcher eventDispatcher,
            BlockingQueue<Completion> completions
    ) {
        this.source = source;
        this.request = request;
        this.eventDispatcher = eventDispatcher;
        this.completions = completions;
    }

    void start(ExecutorService executor, ScheduledExecutorService scheduler, long overallDeadlineNanos) {
        future = executor.submit(this::run);
        long sourceDeadlineNanos = System.nanoTime() + source.timeout().toNanos();
        long timeoutNanos = Math.max(1, Math.min(sourceDeadlineNanos, overallDeadlineNanos) - System.nanoTime());
        timeoutFuture = scheduler.schedule(this::timeout, timeoutNanos, TimeUnit.NANOSECONDS);
        if (terminal.get()) {
            timeoutFuture.cancel(false);
        }
    }

    private void run() {
        try {
            complete(validate(source.search(request, this::emitCandidate)));
        } catch (RuntimeException exception) {
            if (!Thread.currentThread().isInterrupted()) {
                complete(failure(
                        CatalogSourceFailureKind.TRANSIENT_UPSTREAM,
                        "Catalog discovery source failed unexpectedly"
                ));
            }
        }
    }

    private CatalogSourceResult validate(CatalogSourceResult result) {
        if (result == null
                || !source.sourceIdentity().equals(result.discoverySource())
                || result.operation() != CatalogSourceOperation.SEARCH) {
            return failure(
                    CatalogSourceFailureKind.MALFORMED_RESPONSE,
                    "Catalog discovery source returned an invalid result envelope"
            );
        }
        return result;
    }

    private void emitCandidate(ProductCandidate candidate) {
        eventDispatcher.candidate(source.sourceIdentity(), candidate);
    }

    private void complete(CatalogSourceResult result) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        cancelTimeout();
        try {
            if (result.successful()) {
                eventDispatcher.sourceComplete(source.sourceIdentity());
            } else {
                eventDispatcher.sourceDegraded(source.sourceIdentity(), result.failure());
            }
        } finally {
            completions.add(new Completion(result));
        }
    }

    void timeout() {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        Future<?> running = future;
        if (running != null) {
            running.cancel(true);
        }
        CatalogSourceResult result = failure(
                CatalogSourceFailureKind.TIMEOUT,
                "Catalog discovery source exceeded its deadline"
        );
        try {
            eventDispatcher.sourceDegraded(source.sourceIdentity(), result.failure());
        } finally {
            completions.add(new Completion(result));
        }
    }

    private CatalogSourceResult failure(CatalogSourceFailureKind kind, String message) {
        return new CatalogSourceResult(
                source.sourceIdentity().provider(),
                source.sourceIdentity(),
                CatalogSourceOperation.SEARCH,
                null,
                NegotiatedCapabilities.none(),
                List.of(),
                null,
                false,
                new CatalogSourceFailure(kind, message, null, null)
        );
    }

    void cancel() {
        Future<?> running = future;
        if (running != null && !running.isDone()) {
            running.cancel(true);
        }
        cancelTimeout();
    }

    private void cancelTimeout() {
        ScheduledFuture<?> scheduled = timeoutFuture;
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }

    record Completion(CatalogSourceResult result) {
    }
}
