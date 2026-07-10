package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryEvent;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** One independently cancellable source execution with per-source event and deadline admission. */
final class FederatedCatalogSourceTask {

    private final CatalogDiscoverySource source;
    private final CatalogDiscoveryRequest request;
    private final Consumer<CatalogDiscoveryEvent> eventConsumer;
    private final AtomicBoolean acceptingEvents;
    private final BlockingQueue<Completion> completions;
    private final AtomicInteger acceptedCandidates;
    private final int overallCandidateLimit;
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicInteger emittedCandidates = new AtomicInteger();

    private volatile Future<?> future;
    private volatile ScheduledFuture<?> timeoutFuture;

    FederatedCatalogSourceTask(
            CatalogDiscoverySource source,
            CatalogDiscoveryRequest request,
            Consumer<CatalogDiscoveryEvent> eventConsumer,
            AtomicBoolean acceptingEvents,
            BlockingQueue<Completion> completions,
            AtomicInteger acceptedCandidates,
            int overallCandidateLimit
    ) {
        this.source = source;
        this.request = request;
        this.eventConsumer = eventConsumer;
        this.acceptingEvents = acceptingEvents;
        this.completions = completions;
        this.acceptedCandidates = acceptedCandidates;
        this.overallCandidateLimit = overallCandidateLimit;
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
        if (candidate == null || terminal.get() || !acceptingEvents.get()) {
            return;
        }
        if (emittedCandidates.incrementAndGet() > request.candidateLimit()) {
            return;
        }
        if (acceptedCandidates.incrementAndGet() > overallCandidateLimit) {
            return;
        }
        eventConsumer.accept(CatalogDiscoveryEvent.candidate(source.sourceIdentity(), candidate));
    }

    private void complete(CatalogSourceResult result) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        cancelTimeout();
        if (acceptingEvents.get()) {
            eventConsumer.accept(result.successful()
                    ? CatalogDiscoveryEvent.sourceComplete(source.sourceIdentity())
                    : CatalogDiscoveryEvent.sourceDegraded(source.sourceIdentity(), result.failure()));
        }
        completions.add(new Completion(result));
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
        if (acceptingEvents.get()) {
            eventConsumer.accept(CatalogDiscoveryEvent.sourceDegraded(source.sourceIdentity(), result.failure()));
        }
        completions.add(new Completion(result));
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
