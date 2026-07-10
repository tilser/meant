package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryEvent;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryRequest;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceResult;
import com.meant.api.plugin.catalog.common.dto.FederatedCatalogDiscoveryResult;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.service.FederatedCatalogSourceTask.Completion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Runs eligible catalog sources concurrently and owns the request-wide lifecycle. */
@Service
public class FederatedCatalogDiscoveryService {

    private static final Comparator<CatalogDiscoverySource> SOURCE_ORDER = Comparator
            .comparing((CatalogDiscoverySource source) -> source.sourceIdentity().provider().value())
            .thenComparing(source -> source.sourceIdentity().type().name())
            .thenComparing(source -> source.sourceIdentity().value());

    private final List<CatalogDiscoverySource> sources;
    private final Duration overallDeadline;
    private final FederatedCatalogDiscoveryMetrics metrics;

    @Autowired
    public FederatedCatalogDiscoveryService(
            List<CatalogDiscoverySource> sources,
            FederatedCatalogDiscoveryProperties properties,
            FederatedCatalogDiscoveryMetrics metrics
    ) {
        this(sources, properties.overallDeadline(), metrics);
    }

    FederatedCatalogDiscoveryService(
            List<CatalogDiscoverySource> sources,
            Duration overallDeadline,
            FederatedCatalogDiscoveryMetrics metrics
    ) {
        this.sources = sources == null ? List.of() : sources.stream().sorted(SOURCE_ORDER).toList();
        this.overallDeadline = overallDeadline;
        this.metrics = metrics;
    }

    public FederatedCatalogDiscoveryResult search(CatalogDiscoveryRequest request) {
        return search(request, ignored -> { });
    }

    public FederatedCatalogDiscoveryResult search(
            CatalogDiscoveryRequest request,
            Consumer<CatalogDiscoveryEvent> eventConsumer
    ) {
        List<CatalogDiscoverySource> eligibleSources = sources.stream()
                .filter(source -> source.supports(request))
                .toList();
        if (eligibleSources.isEmpty()) {
            FederatedCatalogDiscoveryResult failed = new FederatedCatalogDiscoveryResult(
                    CatalogDiscoveryTerminalStatus.FAILED,
                    List.of(),
                    List.of()
            );
            eventConsumer.accept(CatalogDiscoveryEvent.terminal(failed.status()));
            metrics.requestCompleted(failed.status());
            return failed;
        }

        AtomicBoolean acceptingEvents = new AtomicBoolean(true);
        BlockingQueue<Completion> completions = new LinkedBlockingQueue<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        List<FederatedCatalogSourceTask> tasks = new ArrayList<>();
        long deadlineNanos = System.nanoTime() + overallDeadline.toNanos();
        int sourceCandidateLimit = Math.max(1,
                (request.candidateLimit() + eligibleSources.size() - 1) / eligibleSources.size());
        Set<ProviderIdentity> coveredProviders = eligibleSources.stream()
                .map(CatalogDiscoverySource::sourceIdentity)
                .filter(identity -> identity.type() == ResultSourceType.PROVIDER_CATALOG)
                .map(identity -> identity.provider())
                .collect(Collectors.toUnmodifiableSet());
        CatalogDiscoveryRequest coveredRequest = request.withCoveredProviders(coveredProviders);
        AtomicInteger acceptedCandidates = new AtomicInteger();

        try {
            for (CatalogDiscoverySource source : eligibleSources) {
                FederatedCatalogSourceTask task = new FederatedCatalogSourceTask(
                        source,
                        coveredRequest.withCandidateLimit(sourceCandidateLimit),
                        eventConsumer,
                        acceptingEvents,
                        completions,
                        acceptedCandidates,
                        request.candidateLimit()
                );
                tasks.add(task);
                task.start(executor, scheduler, deadlineNanos);
            }

            List<Completion> completed = await(tasks, completions, deadlineNanos);
            acceptingEvents.set(false);
            return finish(completed, request.candidateLimit(), eventConsumer);
        } catch (InterruptedException exception) {
            acceptingEvents.set(false);
            tasks.forEach(FederatedCatalogSourceTask::cancel);
            metrics.cancelled();
            Thread.currentThread().interrupt();
            throw new CancellationException("Federated catalog discovery was cancelled");
        } finally {
            acceptingEvents.set(false);
            tasks.forEach(FederatedCatalogSourceTask::cancel);
            scheduler.shutdownNow();
            executor.shutdownNow();
        }
    }

    private List<Completion> await(
            List<FederatedCatalogSourceTask> tasks,
            BlockingQueue<Completion> completions,
            long deadlineNanos
    ) throws InterruptedException {
        List<Completion> completed = new ArrayList<>();
        while (completed.size() < tasks.size()) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                tasks.forEach(FederatedCatalogSourceTask::timeout);
                remainingNanos = TimeUnit.MILLISECONDS.toNanos(100);
            }
            Completion completion = completions.poll(remainingNanos, TimeUnit.NANOSECONDS);
            if (completion == null) {
                tasks.forEach(FederatedCatalogSourceTask::timeout);
                continue;
            }
            completed.add(completion);
        }
        return completed;
    }

    private FederatedCatalogDiscoveryResult finish(
            List<Completion> completed,
            int candidateLimit,
            Consumer<CatalogDiscoveryEvent> eventConsumer
    ) {
        List<CatalogSourceResult> results = completed.stream()
                .sorted(Comparator.comparing(completion -> completion.result().discoverySource().value()))
                .map(Completion::result)
                .toList();
        long successfulSources = results.stream().filter(CatalogSourceResult::successful).count();
        CatalogDiscoveryTerminalStatus status = successfulSources == results.size()
                ? CatalogDiscoveryTerminalStatus.SUCCESS
                : successfulSources == 0
                        ? CatalogDiscoveryTerminalStatus.FAILED
                        : CatalogDiscoveryTerminalStatus.PARTIAL;
        List<ProductCandidate> candidates = status == CatalogDiscoveryTerminalStatus.FAILED
                ? List.of()
                : results.stream()
                        .filter(CatalogSourceResult::successful)
                        .flatMap(result -> result.candidates().stream())
                        .limit(candidateLimit)
                        .toList();

        if (status == CatalogDiscoveryTerminalStatus.PARTIAL) {
            results.stream().filter(result -> !result.successful()).forEach(metrics::partialFailure);
        }
        metrics.requestCompleted(status);
        eventConsumer.accept(CatalogDiscoveryEvent.terminal(status));
        return new FederatedCatalogDiscoveryResult(status, results, candidates);
    }

}
