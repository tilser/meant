package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryEvent;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.FederatedCatalogDiscoveryResult;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.properties.FederatedCatalogDiscoveryProperties;
import com.meant.api.module.catalog.service.port.CatalogDiscoverySource;
import com.meant.api.module.catalog.service.FederatedCatalogSourceTask.Completion;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Runs eligible catalog sources concurrently and owns the request-wide lifecycle. */
@Service
public class FederatedCatalogDiscoveryService {

    private static final Comparator<DiscoverySourceIdentity> DISCOVERY_SOURCE_ORDER = Comparator
            .comparing((DiscoverySourceIdentity source) -> source.provider().value())
            .thenComparing(source -> source.type().name())
            .thenComparing(DiscoverySourceIdentity::value);
    private static final Comparator<CatalogDiscoverySource> SOURCE_ORDER =
            Comparator.comparing(CatalogDiscoverySource::sourceIdentity, DISCOVERY_SOURCE_ORDER);

    private final List<CatalogDiscoverySource> sources;
    private final Duration overallDeadline;
    private final FederatedCatalogDiscoveryMetrics metrics;
    private final ScheduledExecutorService deadlineScheduler;

    @Autowired
    public FederatedCatalogDiscoveryService(
            List<CatalogDiscoverySource> sources,
            FederatedCatalogDiscoveryProperties properties,
            FederatedCatalogDiscoveryMetrics metrics,
            ScheduledExecutorService catalogDiscoveryDeadlineScheduler
    ) {
        this(sources, properties.overallDeadline(), metrics, catalogDiscoveryDeadlineScheduler);
    }

    FederatedCatalogDiscoveryService(
            List<CatalogDiscoverySource> sources,
            Duration overallDeadline,
            FederatedCatalogDiscoveryMetrics metrics,
            ScheduledExecutorService deadlineScheduler
    ) {
        this.sources = sources == null ? List.of() : sources.stream().sorted(SOURCE_ORDER).toList();
        this.overallDeadline = overallDeadline;
        this.metrics = metrics;
        this.deadlineScheduler = deadlineScheduler;
    }

    public FederatedCatalogDiscoveryResult search(CatalogDiscoveryRequest request) {
        return search(request, ignored -> { });
    }

    public FederatedCatalogDiscoveryResult search(
            CatalogDiscoveryRequest request,
            Consumer<CatalogDiscoveryEvent> eventConsumer
    ) {
        long deadlineNanos = System.nanoTime() + overallDeadline.toNanos();
        List<CatalogDiscoverySource> eligibleSources = sources.stream()
                .filter(source -> source.supports(request))
                .toList();
        FederatedCatalogEventDispatcher eventDispatcher = new FederatedCatalogEventDispatcher(
                eventConsumer,
                request.candidateLimit()
        );
        if (eligibleSources.isEmpty()) {
            FederatedCatalogDiscoveryResult failed = new FederatedCatalogDiscoveryResult(
                    CatalogDiscoveryTerminalStatus.FAILED,
                    List.of(),
                    List.of(),
                    false
            );
            eventDispatcher.requestTerminal(failed.status());
            metrics.requestCompleted(failed.status());
            awaitTerminal(eventDispatcher, deadlineNanos);
            return failed;
        }

        BlockingQueue<Completion> completions = new LinkedBlockingQueue<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<FederatedCatalogSourceTask> tasks = new ArrayList<>();
        Set<ProviderIdentity> coveredProviders = eligibleSources.stream()
                .map(CatalogDiscoverySource::sourceIdentity)
                .filter(identity -> identity.type() == ResultSourceType.PROVIDER_CATALOG)
                .map(identity -> identity.provider())
                .collect(Collectors.toUnmodifiableSet());
        CatalogDiscoveryRequest coveredRequest = request.withCoveredProviders(coveredProviders);

        try {
            for (CatalogDiscoverySource source : eligibleSources) {
                FederatedCatalogSourceTask task = new FederatedCatalogSourceTask(
                        source,
                        coveredRequest,
                        eventDispatcher,
                        completions
                );
                tasks.add(task);
                task.start(executor, deadlineScheduler, deadlineNanos);
            }

            List<Completion> completed = await(tasks, completions, deadlineNanos);
            FederatedCatalogDiscoveryResult result = finish(completed, request.candidateLimit());
            eventDispatcher.requestTerminal(result.status());
            eventDispatcher.awaitTerminal(deadlineNanos);
            return result;
        } catch (InterruptedException exception) {
            eventDispatcher.cancel();
            tasks.forEach(FederatedCatalogSourceTask::cancel);
            metrics.cancelled();
            Thread.currentThread().interrupt();
            throw new CancellationException("Federated catalog discovery was cancelled");
        } finally {
            tasks.forEach(FederatedCatalogSourceTask::cancel);
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
            int candidateLimit
    ) {
        List<CatalogSourceResult> results = completed.stream()
                .sorted(Comparator.comparing(
                        completion -> completion.result().discoverySource(),
                        DISCOVERY_SOURCE_ORDER
                ))
                .map(Completion::result)
                .toList();
        long successfulSources = results.stream().filter(CatalogSourceResult::successful).count();
        CatalogDiscoveryTerminalStatus status = successfulSources == results.size()
                ? CatalogDiscoveryTerminalStatus.SUCCESS
                : successfulSources == 0
                        ? CatalogDiscoveryTerminalStatus.FAILED
                        : CatalogDiscoveryTerminalStatus.PARTIAL;
        List<CatalogSourceResult> successfulResults = results.stream()
                .filter(CatalogSourceResult::successful)
                .toList();
        List<ProductCandidate> candidates = status == CatalogDiscoveryTerminalStatus.FAILED
                ? List.of()
                : roundRobin(successfulResults, candidateLimit);
        boolean truncated = status != CatalogDiscoveryTerminalStatus.FAILED
                && (successfulResults.stream().mapToInt(result -> result.candidates().size()).sum() > candidateLimit
                        || successfulResults.stream().anyMatch(this::sourceHasMore));

        results.stream().filter(result -> !result.successful()).forEach(metrics::sourceFailure);
        metrics.requestCompleted(status);
        return new FederatedCatalogDiscoveryResult(status, results, candidates, truncated);
    }

    private List<ProductCandidate> roundRobin(List<CatalogSourceResult> results, int candidateLimit) {
        List<ProductCandidate> merged = new ArrayList<>(candidateLimit);
        for (int index = 0; merged.size() < candidateLimit; index++) {
            boolean added = false;
            for (CatalogSourceResult result : results) {
                if (index < result.candidates().size()) {
                    merged.add(result.candidates().get(index));
                    added = true;
                    if (merged.size() == candidateLimit) {
                        break;
                    }
                }
            }
            if (!added) {
                break;
            }
        }
        return List.copyOf(merged);
    }

    private boolean sourceHasMore(CatalogSourceResult result) {
        return result.truncated() || result.page() != null && result.page().hasNextPage();
    }

    private void awaitTerminal(FederatedCatalogEventDispatcher eventDispatcher, long deadlineNanos) {
        try {
            eventDispatcher.awaitTerminal(deadlineNanos);
        } catch (InterruptedException exception) {
            eventDispatcher.cancel();
            Thread.currentThread().interrupt();
            throw new CancellationException("Federated catalog discovery was cancelled");
        }
    }

}
