package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryEvent;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailure;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/** Serializes request-local event admission without invoking external callbacks under its state lock. */
@Slf4j
final class FederatedCatalogEventDispatcher {

    private final Consumer<CatalogDiscoveryEvent> eventConsumer;
    private final int candidateLimit;
    private final BlockingQueue<CatalogDiscoveryEvent> events = new LinkedBlockingQueue<>();
    private final Map<DiscoverySourceIdentity, Set<String>> sourceCandidateKeys = new HashMap<>();
    private final Set<String> requestCandidateKeys = new HashSet<>();
    private final Set<DiscoverySourceIdentity> terminalSources = new HashSet<>();
    private final CompletableFuture<Void> terminalDelivered = new CompletableFuture<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("catalog-discovery-events-", 0).factory()
    );

    private boolean requestTerminal;

    FederatedCatalogEventDispatcher(
            Consumer<CatalogDiscoveryEvent> eventConsumer,
            int candidateLimit
    ) {
        this.eventConsumer = eventConsumer;
        this.candidateLimit = candidateLimit;
        executor.submit(this::deliver);
    }

    synchronized void candidate(DiscoverySourceIdentity source, ProductCandidate candidate) {
        if (candidate == null || requestTerminal || terminalSources.contains(source)) {
            return;
        }
        String candidateKey = admissionKey(source, candidate);
        Set<String> sourceKeys = sourceCandidateKeys.computeIfAbsent(source, ignored -> new HashSet<>());
        boolean update = sourceKeys.contains(candidateKey);
        if (!update && sourceKeys.size() >= candidateLimit) {
            return;
        }
        if (!update && !requestCandidateKeys.contains(candidateKey) && requestCandidateKeys.size() >= candidateLimit) {
            return;
        }
        sourceKeys.add(candidateKey);
        requestCandidateKeys.add(candidateKey);
        events.add(CatalogDiscoveryEvent.candidate(source, candidate));
    }

    private String admissionKey(DiscoverySourceIdentity source, ProductCandidate candidate) {
        if (source.type() != ResultSourceType.MERCHANT_STOREFRONT) {
            return "offer:" + candidate.offer().key();
        }
        ResultProvenance provenance = candidate.provenance().stream()
                .filter(value -> value.sourceReference().type() == source.type())
                .findFirst()
                .orElseGet(() -> candidate.provenance().getFirst());
        String merchant;
        if (provenance.externalMerchantReference() != null) {
            merchant = provenance.externalMerchantReference().namespace()
                    + ":" + provenance.externalMerchantReference().value();
        } else if (provenance.localRouting() != null) {
            merchant = provenance.localRouting().merchantIntegrationId().toString();
        } else {
            merchant = provenance.sourceReference().reference();
        }
        return "candidate:"
                + provenance.provider().value() + ":"
                + merchant + ":"
                + provenance.externalProductReference().namespace() + ":"
                + provenance.externalProductReference().value();
    }

    synchronized void sourceComplete(DiscoverySourceIdentity source) {
        sourceTerminal(source, CatalogDiscoveryEvent.sourceComplete(source));
    }

    synchronized void sourceDegraded(DiscoverySourceIdentity source, CatalogSourceFailure failure) {
        sourceTerminal(source, CatalogDiscoveryEvent.sourceDegraded(source, failure));
    }

    private void sourceTerminal(DiscoverySourceIdentity source, CatalogDiscoveryEvent event) {
        if (requestTerminal || !terminalSources.add(source)) {
            return;
        }
        events.add(event);
    }

    synchronized void requestTerminal(CatalogDiscoveryTerminalStatus status) {
        if (requestTerminal) {
            return;
        }
        requestTerminal = true;
        events.add(CatalogDiscoveryEvent.terminal(status));
        executor.shutdown();
    }

    void awaitTerminal(long deadlineNanos) throws InterruptedException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            return;
        }
        try {
            terminalDelivered.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (java.util.concurrent.ExecutionException | TimeoutException ignored) {
            // Callback delivery is best effort and must never extend the request deadline.
        }
    }

    synchronized void cancel() {
        requestTerminal = true;
        events.clear();
        terminalDelivered.complete(null);
        executor.shutdownNow();
    }

    private void deliver() {
        try {
            while (true) {
                CatalogDiscoveryEvent event = events.take();
                try {
                    eventConsumer.accept(event);
                } catch (RuntimeException exception) {
                    log.warn("Catalog discovery event callback failed. eventType={}, failureType={}",
                            event.type(), exception.getClass().getName());
                }
                if (event.terminalStatus() != null) {
                    terminalDelivered.complete(null);
                    return;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            terminalDelivered.complete(null);
        }
    }
}
