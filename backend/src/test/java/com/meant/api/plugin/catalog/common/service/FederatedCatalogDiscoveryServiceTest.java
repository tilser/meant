package com.meant.api.plugin.catalog.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryEvent;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryEventType;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryRequest;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailure;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailureKind;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceOperation;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceResult;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.FederatedCatalogDiscoveryResult;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.OfferMerchantScope;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceReference;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class FederatedCatalogDiscoveryServiceTest {

    @Test
    void startsBothSourcesConcurrentlyAndCompletesExactlyOnce() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        SourceBehavior concurrentSuccess = (source, request, consumer) -> {
            bothStarted.countDown();
            await(bothStarted);
            ProductCandidate candidate = candidate(source, source.value());
            consumer.accept(candidate);
            return success(source, List.of(candidate));
        };
        FakeSource generic = source("GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC", concurrentSuccess);
        FakeSource shopify = source("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL", concurrentSuccess);
        CopyOnWriteArrayList<CatalogDiscoveryEvent> events = new CopyOnWriteArrayList<>();

        FederatedCatalogDiscoveryResult result = service(List.of(generic, shopify), Duration.ofSeconds(2))
                .search(request(8), events::add);

        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.SUCCESS);
        assertThat(result.candidates()).hasSize(2);
        assertThat(events).filteredOn(event -> event.type() == CatalogDiscoveryEventType.CANDIDATE)
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.observationSources())
                        .containsExactlyElementsOf(event.candidate().provenance().stream()
                                .map(ResultProvenance::discoverySource)
                                .distinct()
                                .toList()));
        assertThat(events).filteredOn(event -> event.type() == CatalogDiscoveryEventType.COMPLETE
                        || event.type() == CatalogDiscoveryEventType.ERROR)
                .singleElement()
                .extracting(CatalogDiscoveryEvent::terminalStatus)
                .isEqualTo(CatalogDiscoveryTerminalStatus.SUCCESS);
        assertThat(generic.calls()).isOne();
        assertThat(shopify.calls()).isOne();
    }

    @Test
    void throwingSourceCompletionCallbackCannotPreventTerminalResult() {
        FakeSource source = source(
                "GENERIC_UCP",
                ResultSourceType.MERCHANT_STOREFRONT,
                "GENERIC",
                (identity, request, consumer) -> success(identity, List.of(candidate(identity, "safe")))
        );
        AtomicInteger requestTerminals = new AtomicInteger();

        FederatedCatalogDiscoveryResult result = assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> service(List.of(source), Duration.ofMillis(500)).search(request(4), event -> {
                    if (event.type() == CatalogDiscoveryEventType.SOURCE_COMPLETE) {
                        throw new IllegalStateException("callback failed");
                    }
                    if (event.terminalStatus() != null) {
                        requestTerminals.incrementAndGet();
                    }
                })
        );

        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.SUCCESS);
        assertThat(requestTerminals).hasValue(1);
    }

    @Test
    void throwingTimeoutDegradationCallbackCannotPreventTerminalResult() {
        FakeSource source = source(
                "GENERIC_UCP",
                ResultSourceType.MERCHANT_STOREFRONT,
                "GENERIC",
                Duration.ofMillis(50),
                (identity, request, consumer) -> {
                    awaitInterrupt();
                    return success(identity, List.of());
                }
        );
        AtomicInteger requestTerminals = new AtomicInteger();

        FederatedCatalogDiscoveryResult result = assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> service(List.of(source), Duration.ofMillis(500)).search(request(4), event -> {
                    if (event.type() == CatalogDiscoveryEventType.SOURCE_DEGRADED) {
                        throw new IllegalStateException("callback failed");
                    }
                    if (event.terminalStatus() != null) {
                        requestTerminals.incrementAndGet();
                    }
                })
        );

        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.FAILED);
        assertThat(result.sources()).singleElement().satisfies(sourceResult ->
                assertThat(sourceResult.failure().kind()).isEqualTo(CatalogSourceFailureKind.TIMEOUT));
        assertThat(requestTerminals).hasValue(1);
    }

    @Test
    void timeoutAdmissionRejectsCandidateReleasedAfterSourceTerminal() throws Exception {
        CountDownLatch degradedDelivered = new CountDownLatch(1);
        CountDownLatch candidateAttempted = new CountDownLatch(1);
        SourceBehavior behavior = (source, request, consumer) -> {
            Thread.ofVirtual().start(() -> {
                await(degradedDelivered);
                consumer.accept(candidate(source, "late"));
                candidateAttempted.countDown();
            });
            awaitInterrupt();
            return success(source, List.of());
        };
        CopyOnWriteArrayList<CatalogDiscoveryEvent> events = new CopyOnWriteArrayList<>();

        FederatedCatalogDiscoveryResult result = service(List.of(source(
                "GENERIC_UCP",
                ResultSourceType.MERCHANT_STOREFRONT,
                "GENERIC",
                Duration.ofMillis(50),
                behavior
        )), Duration.ofMillis(500)).search(request(4), event -> {
            events.add(event);
            if (event.type() == CatalogDiscoveryEventType.SOURCE_DEGRADED) {
                degradedDelivered.countDown();
            }
        });

        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.FAILED);
        assertThat(candidateAttempted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(events).noneMatch(event -> event.type() == CatalogDiscoveryEventType.CANDIDATE);
        assertTerminalOrdering(events);
    }

    @Test
    void requestTerminalAdmissionRejectsCandidateReleasedAfterTerminal() throws Exception {
        CountDownLatch requestTerminalDelivered = new CountDownLatch(1);
        CountDownLatch candidateAttempted = new CountDownLatch(1);
        SourceBehavior behavior = (source, request, consumer) -> {
            Thread.ofVirtual().start(() -> {
                await(requestTerminalDelivered);
                consumer.accept(candidate(source, "late"));
                candidateAttempted.countDown();
            });
            return success(source, List.of());
        };
        CopyOnWriteArrayList<CatalogDiscoveryEvent> events = new CopyOnWriteArrayList<>();

        FederatedCatalogDiscoveryResult result = service(List.of(source(
                "GENERIC_UCP",
                ResultSourceType.MERCHANT_STOREFRONT,
                "GENERIC",
                behavior
        )), Duration.ofSeconds(1)).search(request(4), event -> {
            events.add(event);
            if (event.terminalStatus() != null) {
                requestTerminalDelivered.countDown();
            }
        });

        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.SUCCESS);
        assertThat(candidateAttempted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(events).noneMatch(event -> event.type() == CatalogDiscoveryEventType.CANDIDATE);
        assertTerminalOrdering(events);
    }

    @Test
    void returnsPartialWhenShopifyFailsAndGenericSucceeds() {
        assertPartial("SHOPIFY", "GENERIC_UCP");
    }

    @Test
    void returnsPartialWhenGenericFailsAndShopifySucceeds() {
        assertPartial("GENERIC_UCP", "SHOPIFY");
    }

    @Test
    void bothFailuresProduceDeterministicTypedErrorTerminal() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        SourceBehavior failure = (source, request, consumer) -> {
            bothStarted.countDown();
            await(bothStarted);
            return failure(source, CatalogSourceFailureKind.RATE_LIMITED);
        };
        CopyOnWriteArrayList<CatalogDiscoveryEvent> events = new CopyOnWriteArrayList<>();

        FederatedCatalogDiscoveryResult result = service(List.of(
                source("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL", failure),
                source("GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC", failure)
        ), Duration.ofSeconds(2)).search(request(8), events::add);

        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.FAILED);
        assertThat(result.candidates()).isEmpty();
        assertThat(result.sources()).extracting(source -> source.failure().kind())
                .containsOnly(CatalogSourceFailureKind.RATE_LIMITED);
        assertThat(events).filteredOn(event -> event.type() == CatalogDiscoveryEventType.ERROR)
                .singleElement()
                .extracting(CatalogDiscoveryEvent::terminalStatus)
                .isEqualTo(CatalogDiscoveryTerminalStatus.FAILED);
    }

    @Test
    void overallDeadlineBoundsCompletionAndCancelsSlowSources() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(2);
        SourceBehavior blocked = (source, request, consumer) -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
            }
            return success(source, List.of());
        };

        FederatedCatalogDiscoveryResult result = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> service(List.of(
                source("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL", Duration.ofSeconds(5), blocked),
                source("GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC", Duration.ofSeconds(5), blocked)
        ), Duration.ofMillis(100)).search(request(8)));

        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.FAILED);
        assertThat(result.sources()).extracting(source -> source.failure().kind())
                .containsOnly(CatalogSourceFailureKind.TIMEOUT);
        assertThat(await(interrupted, 1)).isTrue();
    }

    @Test
    void cancellingRequestInterruptsSourcesAndSuppressesLateEvents() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch interrupted = new CountDownLatch(2);
        SourceBehavior blocked = (source, request, consumer) -> {
            bothStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                consumer.accept(candidate(source, "late"));
                Thread.currentThread().interrupt();
            }
            return success(source, List.of());
        };
        CopyOnWriteArrayList<CatalogDiscoveryEvent> events = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> requestFuture = executor.submit(() -> service(List.of(
                    source("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL", blocked),
                    source("GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC", blocked)
            ), Duration.ofSeconds(5)).search(request(8), events::add));

            assertThat(await(bothStarted, 1)).isTrue();
            requestFuture.cancel(true);

            assertThat(await(interrupted, 1)).isTrue();
            assertThat(events).noneMatch(event -> event.type() == CatalogDiscoveryEventType.CANDIDATE);
            assertThat(events).noneMatch(event -> event.type() == CatalogDiscoveryEventType.COMPLETE
                    || event.type() == CatalogDiscoveryEventType.ERROR);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recordsProviderScopedPartialMetricsWithoutPayloadTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FederatedCatalogDiscoveryMetrics metrics = new FederatedCatalogDiscoveryMetrics(registry);
        FakeSource shopify = source(
                "SHOPIFY",
                ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL",
                (source, request, consumer) -> failure(source, CatalogSourceFailureKind.AUTHENTICATION)
        );
        FakeSource generic = source(
                "GENERIC_UCP",
                ResultSourceType.MERCHANT_STOREFRONT,
                "GENERIC",
                (source, request, consumer) -> success(source, List.of(candidate(source, "safe")))
        );

        FederatedCatalogDiscoveryResult result = new FederatedCatalogDiscoveryService(
                List.of(shopify, generic), Duration.ofSeconds(1), metrics
        ).search(request(8));

        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.PARTIAL);
        assertThat(registry.get("commerce.catalog.federation.requests").tag("outcome", "partial").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("commerce.catalog.federation.source.failures")
                .tag("provider", "shopify")
                .tag("source", "SHOPIFY_GLOBAL")
                .tag("failure", "authentication")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .noneMatch(tag -> tag.getKey().contains("query")
                        || tag.getKey().contains("product")
                        || tag.getKey().contains("token")));
    }

    @Test
    void boundsPerSourceAndOverallCandidateBudgets() {
        SourceBehavior fillsBudget = (source, request, consumer) -> {
            List<ProductCandidate> candidates = java.util.stream.IntStream.range(0, request.candidateLimit())
                    .mapToObj(index -> candidate(source, source.value() + index))
                    .toList();
            candidates.forEach(consumer);
            return success(source, candidates);
        };
        FakeSource shopify = source(
                "SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL", fillsBudget);
        FakeSource generic = source(
                "GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC", fillsBudget);
        CopyOnWriteArrayList<CatalogDiscoveryEvent> events = new CopyOnWriteArrayList<>();

        FederatedCatalogDiscoveryResult result = service(List.of(shopify, generic), Duration.ofSeconds(1))
                .search(request(3), events::add);

        assertThat(result.candidates()).hasSize(3);
        assertThat(events).filteredOn(event -> event.type() == CatalogDiscoveryEventType.CANDIDATE)
                .hasSize(3);
    }

    @Test
    void candidateUpdatesDoNotConsumeAdditionalUniqueBudget() {
        FakeSource generic = source(
                "GENERIC_UCP",
                ResultSourceType.MERCHANT_STOREFRONT,
                "GENERIC",
                (source, request, consumer) -> {
                    ProductCandidate preliminary = candidate(source, "stable-product", "preliminary-variant");
                    ProductCandidate enriched = candidate(source, "stable-product", "enriched-variant");
                    consumer.accept(preliminary);
                    consumer.accept(enriched);
                    consumer.accept(candidate(source, "bounded-product", "bounded-variant"));
                    return success(source, List.of(enriched));
                }
        );
        CopyOnWriteArrayList<CatalogDiscoveryEvent> events = new CopyOnWriteArrayList<>();

        FederatedCatalogDiscoveryResult result = service(List.of(generic), Duration.ofSeconds(1))
                .search(request(1), events::add);

        assertThat(result.candidates()).singleElement();
        assertThat(events).filteredOn(event -> event.type() == CatalogDiscoveryEventType.CANDIDATE)
                .extracting(event -> event.candidate().offer().identity().externalVariantIdentity().value())
                .containsExactly("variant-preliminary-variant", "variant-enriched-variant");
    }

    @Test
    void roundRobinMergeIsPrefixStableAcrossSuccessiveFetchLimits() {
        List<ProductCandidate> genericCandidates = candidates(
                sourceIdentity("GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC"), "g", 8);
        List<ProductCandidate> shopifyCandidates = candidates(
                sourceIdentity("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL"), "s", 8);
        FakeSource generic = prefixSource(
                sourceIdentity("GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC"),
                genericCandidates
        );
        FakeSource shopify = prefixSource(
                sourceIdentity("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL"),
                shopifyCandidates
        );

        FederatedCatalogDiscoveryResult first = service(List.of(generic, shopify), Duration.ofSeconds(1))
                .search(request(3));
        FederatedCatalogDiscoveryResult second = service(List.of(generic, shopify), Duration.ofSeconds(1))
                .search(request(5));
        FederatedCatalogDiscoveryResult third = service(List.of(generic, shopify), Duration.ofSeconds(1))
                .search(request(7));

        assertThat(second.candidates().subList(0, first.candidates().size()))
                .containsExactlyElementsOf(first.candidates());
        assertThat(third.candidates().subList(0, second.candidates().size()))
                .containsExactlyElementsOf(second.candidates());
        assertThat(third.candidates().stream()
                .map(candidate -> candidate.provenance().getFirst().externalProductReference().value()))
                .containsExactly("product-g0", "product-s0", "product-g1", "product-s1",
                        "product-g2", "product-s2", "product-g3");
        assertThat(first.truncated()).isTrue();
        assertThat(second.truncated()).isTrue();
        assertThat(third.truncated()).isTrue();
    }

    @Test
    void sparseAndFailedSourcesDoNotUnderfillATruncatedSuccessfulSource() {
        DiscoverySourceIdentity genericIdentity =
                sourceIdentity("GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC");
        DiscoverySourceIdentity shopifyIdentity =
                sourceIdentity("SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL");
        FakeSource sparse = prefixSource(genericIdentity, candidates(genericIdentity, "g", 1));
        FakeSource shopify = prefixSource(shopifyIdentity, candidates(shopifyIdentity, "s", 8));

        FederatedCatalogDiscoveryResult sparseResult = service(List.of(sparse, shopify), Duration.ofSeconds(1))
                .search(request(5));
        FakeSource failed = source(
                "GENERIC_UCP",
                ResultSourceType.MERCHANT_STOREFRONT,
                "GENERIC",
                (source, request, consumer) -> failure(source, CatalogSourceFailureKind.TRANSIENT_UPSTREAM)
        );
        FederatedCatalogDiscoveryResult partialResult = service(List.of(failed, shopify), Duration.ofSeconds(1))
                .search(request(5));

        assertThat(sparseResult.candidates()).hasSize(5);
        assertThat(sparseResult.truncated()).isTrue();
        assertThat(partialResult.status()).isEqualTo(CatalogDiscoveryTerminalStatus.PARTIAL);
        assertThat(partialResult.candidates()).hasSize(5);
        assertThat(partialResult.truncated()).isTrue();
    }

    @Test
    void recordsEveryBoundaryTimeoutForAllFailedAndPartialRequests() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FederatedCatalogDiscoveryMetrics metrics = new FederatedCatalogDiscoveryMetrics(registry);
        SourceBehavior blocked = (source, request, consumer) -> {
            awaitInterrupt();
            return success(source, List.of());
        };
        FakeSource generic = source(
                "GENERIC_UCP", ResultSourceType.MERCHANT_STOREFRONT, "GENERIC", Duration.ofMillis(50), blocked);
        FakeSource shopify = source(
                "SHOPIFY", ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL", Duration.ofMillis(50), blocked);

        FederatedCatalogDiscoveryResult failed = new FederatedCatalogDiscoveryService(
                List.of(generic, shopify), Duration.ofMillis(500), metrics
        ).search(request(4));

        assertThat(failed.status()).isEqualTo(CatalogDiscoveryTerminalStatus.FAILED);
        assertThat(registry.get("commerce.catalog.federation.source.failures")
                .tag("provider", "generic_ucp")
                .tag("source", "GENERIC")
                .tag("failure", "timeout")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("commerce.catalog.federation.source.failures")
                .tag("provider", "shopify")
                .tag("source", "SHOPIFY_GLOBAL")
                .tag("failure", "timeout")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordsBoundaryTimeoutForPartialSuccessExactlyOnce() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FederatedCatalogDiscoveryMetrics metrics = new FederatedCatalogDiscoveryMetrics(registry);
        FakeSource generic = source(
                "GENERIC_UCP",
                ResultSourceType.MERCHANT_STOREFRONT,
                "GENERIC",
                Duration.ofMillis(50),
                (source, request, consumer) -> {
                    awaitInterrupt();
                    return success(source, List.of());
                }
        );
        FakeSource shopify = source(
                "SHOPIFY",
                ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL",
                (source, request, consumer) -> success(source, List.of(candidate(source, "safe")))
        );

        FederatedCatalogDiscoveryResult result = new FederatedCatalogDiscoveryService(
                List.of(generic, shopify), Duration.ofMillis(500), metrics
        ).search(request(4));

        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.PARTIAL);
        assertThat(registry.get("commerce.catalog.federation.source.failures")
                .tag("provider", "generic_ucp")
                .tag("source", "GENERIC")
                .tag("failure", "timeout")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("commerce.catalog.federation.requests")
                .tag("outcome", "partial")
                .counter().count()).isEqualTo(1.0);
    }

    private void assertPartial(String failedProvider, String successfulProvider) {
        ResultSourceType failedType = "SHOPIFY".equals(failedProvider)
                ? ResultSourceType.PROVIDER_CATALOG
                : ResultSourceType.MERCHANT_STOREFRONT;
        ResultSourceType successType = "SHOPIFY".equals(successfulProvider)
                ? ResultSourceType.PROVIDER_CATALOG
                : ResultSourceType.MERCHANT_STOREFRONT;
        FakeSource failed = source(
                failedProvider,
                failedType,
                failedProvider + "_SOURCE",
                (source, request, consumer) -> failure(source, CatalogSourceFailureKind.TIMEOUT)
        );
        FakeSource successful = source(
                successfulProvider,
                successType,
                successfulProvider + "_SOURCE",
                (source, request, consumer) -> {
                    ProductCandidate candidate = candidate(source, "success");
                    consumer.accept(candidate);
                    return success(source, List.of(candidate));
                }
        );

        FederatedCatalogDiscoveryResult result = service(List.of(failed, successful), Duration.ofSeconds(1))
                .search(request(8));

        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.PARTIAL);
        assertThat(result.candidates()).singleElement().satisfies(candidate ->
                assertThat(candidate.provenance().getFirst().provider().value()).isEqualTo(successfulProvider));
    }

    private FederatedCatalogDiscoveryService service(List<CatalogDiscoverySource> sources, Duration deadline) {
        return new FederatedCatalogDiscoveryService(
                sources,
                deadline,
                new FederatedCatalogDiscoveryMetrics(new SimpleMeterRegistry())
        );
    }

    private CatalogDiscoveryRequest request(int limit) {
        return new CatalogDiscoveryRequest("linen shirt", null, limit, null, null, null);
    }

    private FakeSource source(
            String provider,
            ResultSourceType type,
            String value,
            SourceBehavior behavior
    ) {
        return source(provider, type, value, Duration.ofSeconds(2), behavior);
    }

    private FakeSource source(
            String provider,
            ResultSourceType type,
            String value,
            Duration timeout,
            SourceBehavior behavior
    ) {
        return new FakeSource(
                new DiscoverySourceIdentity(new ProviderIdentity(provider), type, value),
                timeout,
                behavior
        );
    }

    private FakeSource prefixSource(
            DiscoverySourceIdentity source,
            List<ProductCandidate> candidates
    ) {
        return new FakeSource(source, Duration.ofSeconds(2), (identity, request, consumer) -> {
            List<ProductCandidate> page = candidates.stream()
                    .limit(request.candidateLimit())
                    .toList();
            page.forEach(consumer);
            return success(identity, page, candidates.size() > page.size());
        });
    }

    private DiscoverySourceIdentity sourceIdentity(String provider, ResultSourceType type, String value) {
        return new DiscoverySourceIdentity(new ProviderIdentity(provider), type, value);
    }

    private List<ProductCandidate> candidates(
            DiscoverySourceIdentity source,
            String prefix,
            int count
    ) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> candidate(source, prefix + index))
                .toList();
    }

    private static CatalogSourceResult success(
            DiscoverySourceIdentity source,
            List<ProductCandidate> candidates
    ) {
        return success(source, candidates, false);
    }

    private static CatalogSourceResult success(
            DiscoverySourceIdentity source,
            List<ProductCandidate> candidates,
            boolean truncated
    ) {
        return new CatalogSourceResult(
                source.provider(),
                source,
                CatalogSourceOperation.SEARCH,
                "2026-04-08",
                NegotiatedCapabilities.none(),
                candidates,
                null,
                truncated,
                null
        );
    }

    private static CatalogSourceResult failure(
            DiscoverySourceIdentity source,
            CatalogSourceFailureKind kind
    ) {
        return new CatalogSourceResult(
                source.provider(),
                source,
                CatalogSourceOperation.SEARCH,
                null,
                NegotiatedCapabilities.none(),
                List.of(),
                null,
                false,
                new CatalogSourceFailure(kind, "Safe classified failure", null, null)
        );
    }

    private static ProductCandidate candidate(DiscoverySourceIdentity source, String value) {
        return candidate(source, value, value);
    }

    private static ProductCandidate candidate(
            DiscoverySourceIdentity source,
            String productValue,
            String variantValue
    ) {
        ProviderIdentity provider = source.provider();
        ExternalIdentifier merchant = new ExternalIdentifier(
                ExternalIdentifierType.MERCHANT,
                provider.value(),
                "merchant"
        );
        ExternalIdentifier product = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT,
                provider.value(),
                "product-" + productValue
        );
        ExternalIdentifier variant = new ExternalIdentifier(
                ExternalIdentifierType.VARIANT,
                provider.value(),
                "variant-" + variantValue
        );
        ResultSourceReference reference = new ResultSourceReference(source.type(), productValue, null);
        ResultProvenance provenance = new ResultProvenance(
                provider,
                source,
                null,
                merchant,
                product,
                variant,
                new ResultFreshness(Instant.parse("2026-07-11T00:00:00Z"), null),
                reference
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        provider,
                        OfferMerchantScope.external(merchant),
                        product,
                        variant,
                        List.of(),
                        List.of(),
                        null
                ),
                "Merchant",
                null,
                null,
                null,
                OfferAvailability.unknown(),
                List.of(),
                null,
                List.of(provenance)
        );
        return new ProductCandidate(
                "Product " + productValue,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(provenance),
                offer
        );
    }

    private static void awaitInterrupt() {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertTerminalOrdering(List<CatalogDiscoveryEvent> events) {
        boolean requestTerminal = false;
        java.util.Set<DiscoverySourceIdentity> terminalSources = new java.util.HashSet<>();
        for (CatalogDiscoveryEvent event : events) {
            assertThat(requestTerminal).as("no event may follow the request terminal").isFalse();
            if (event.type() == CatalogDiscoveryEventType.CANDIDATE) {
                assertThat(terminalSources).doesNotContain(event.source());
            }
            if (event.type() == CatalogDiscoveryEventType.SOURCE_COMPLETE
                    || event.type() == CatalogDiscoveryEventType.SOURCE_DEGRADED) {
                terminalSources.add(event.source());
            }
            requestTerminal = event.terminalStatus() != null;
        }
        assertThat(requestTerminal).isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent source did not start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean await(CountDownLatch latch, int seconds) throws InterruptedException {
        return latch.await(seconds, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface SourceBehavior {
        CatalogSourceResult search(
                DiscoverySourceIdentity source,
                CatalogDiscoveryRequest request,
                Consumer<ProductCandidate> consumer
        );
    }

    private static final class FakeSource implements CatalogDiscoverySource {

        private final DiscoverySourceIdentity source;
        private final Duration timeout;
        private final SourceBehavior behavior;
        private final AtomicInteger calls = new AtomicInteger();

        private FakeSource(DiscoverySourceIdentity source, Duration timeout, SourceBehavior behavior) {
            this.source = source;
            this.timeout = timeout;
            this.behavior = behavior;
        }

        @Override
        public DiscoverySourceIdentity sourceIdentity() {
            return source;
        }

        @Override
        public Duration timeout() {
            return timeout;
        }

        @Override
        public CatalogSourceResult search(
                CatalogDiscoveryRequest request,
                Consumer<ProductCandidate> candidateConsumer
        ) {
            calls.incrementAndGet();
            return behavior.search(source, request, candidateConsumer);
        }

        private int calls() {
            return calls.get();
        }
    }
}
