package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.properties.MerchantCatalogSearchProperties;
import com.meant.api.module.merchant.properties.MerchantMcpToolProperties;
import com.meant.api.module.merchant.service.dto.MerchantCatalogProductCandidate;
import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchAttemptResult;
import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchOutcome;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.VoyageRerankResult;
import com.meant.api.module.user.service.MerchantSemanticCatalogDiscoverySource;
import com.meant.api.module.user.service.UserCanonicalProductCandidateMapper;
import com.meant.api.module.user.service.UserProductSearchHashService;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryEvent;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryEventType;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.module.catalog.service.dto.CatalogSimilarityReference;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.FederatedCatalogDiscoveryResult;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.CatalogDiscoverySourceMetrics;
import com.meant.api.module.catalog.service.FederatedCatalogDiscoveryMetrics;
import com.meant.api.module.catalog.service.FederatedCatalogDiscoveryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MerchantSemanticCatalogDiscoverySourceTest {

    private static final UUID MERCHANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final ScheduledExecutorService DEADLINE_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).factory());

    @Test
    void doesNotParticipateInBroadProviderCatalogSearches() {
        MerchantSemanticCatalogDiscoverySource source = source(mock(MerchantIntegrationLookupService.class));

        assertThat(source.supports(new CatalogDiscoveryRequest(
                "linen shirt",
                null,
                10,
                null,
                null,
                null
        ))).isFalse();
    }

    @Test
    void streamsPreliminaryAndEnrichedCandidateWithinOneUniqueBudgetAndModelsBothSourceSemantics() {
        MerchantIntegrationLookupService integrationLookup = mock(MerchantIntegrationLookupService.class);
        when(integrationLookup.listByMerchant(any())).thenReturn(List.of(integration()));
        MerchantSemanticCatalogDiscoverySource source = source(integrationLookup);
        CopyOnWriteArrayList<CatalogDiscoveryEvent> events = new CopyOnWriteArrayList<>();

        FederatedCatalogDiscoveryResult result = federation(source).search(
                new CatalogDiscoveryRequest("linen shirt", MERCHANT_ID, 1, null, null, null),
                events::add
        );

        List<CatalogDiscoveryEvent> candidates = events.stream()
                .filter(event -> event.type() == CatalogDiscoveryEventType.CANDIDATE)
                .toList();
        assertThat(candidates).hasSize(2);
        assertThat(candidates.getFirst().candidate().description()).isEqualTo("preliminary");
        assertThat(candidates.getLast().candidate().description()).isEqualTo("enriched");
        assertThat(candidates).allSatisfy(event -> {
            assertThat(event.source()).isEqualTo(source.sourceIdentity());
            assertThat(event.observationSources())
                    .containsExactlyElementsOf(event.candidate().provenance().stream()
                            .map(provenance -> provenance.discoverySource())
                            .distinct()
                            .toList())
                    .doesNotContain(event.source());
        });
        assertThat(result.candidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.description()).isEqualTo("enriched"));
        verify(integrationLookup).listByMerchant(any());
    }

    @Test
    void requestsTheEntireScopedWindowAndPreservesUpstreamTruncation() {
        MerchantIntegrationLookupService integrationLookup = mock(MerchantIntegrationLookupService.class);
        when(integrationLookup.listByMerchant(any())).thenReturn(List.of(integration()));
        MerchantCatalogSearchExecutor catalogExecutor = mock(MerchantCatalogSearchExecutor.class);
        MerchantSemanticCatalogDiscoverySource source = source(integrationLookup, catalogExecutor, true);

        FederatedCatalogDiscoveryResult result = federation(source).search(
                new CatalogDiscoveryRequest("linen shirt", MERCHANT_ID, 100, null, null, null)
        );

        ArgumentCaptor<Integer> productsPerMerchant = ArgumentCaptor.forClass(Integer.class);
        verify(catalogExecutor).search(
                any(), any(), any(), any(), productsPerMerchant.capture(), anyList());
        assertThat(productsPerMerchant.getValue()).isEqualTo(100);
        assertThat(result.truncated()).isTrue();
        assertThat(result.sources()).singleElement()
                .satisfies(sourceResult -> assertThat(sourceResult.truncated()).isTrue());
    }

    @Test
    void cachesAnUnresolvedIntegrationOnceForAllRequestObservations() {
        MerchantIntegrationLookupService integrationLookup = mock(MerchantIntegrationLookupService.class);
        when(integrationLookup.listByMerchant(any())).thenReturn(List.of());
        MerchantSemanticCatalogDiscoverySource source = source(integrationLookup);

        FederatedCatalogDiscoveryResult result = federation(source).search(
                new CatalogDiscoveryRequest("linen shirt", MERCHANT_ID, 1, null, null, null)
        );

        assertThat(result.candidates()).isEmpty();
        verify(integrationLookup).listByMerchant(any());
    }

    @Test
    void declinesSimilarityRequestsEvenWhenTheyAlsoContainAQuery() {
        MerchantSemanticCatalogDiscoverySource source = new MerchantSemanticCatalogDiscoverySource(
                null,
                null,
                null,
                null,
                null,
                null,
                new MerchantMcpToolProperties(100, 100, 500, Duration.ofMinutes(1))
        );
        ProviderIdentity provider = new ProviderIdentity("GENERIC_UCP");
        CatalogSimilarityReference similarityReference = new CatalogSimilarityReference(
                provider,
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, provider.value(), "product-1")
        );

        assertThat(source.supports(new CatalogDiscoveryRequest(
                "linen shirt", MERCHANT_ID, 1, null, null, null, similarityReference
        ))).isFalse();
    }

    @Test
    void merchantScopedFederationFailsClosedForUnsupportedTypedExtensionConstraints() {
        MerchantSemanticCatalogDiscoverySource source = source(mock(MerchantIntegrationLookupService.class));
        CatalogDiscoveryFilters filters = new CatalogDiscoveryFilters(
                true,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(new CatalogDiscoveryAttributeFilter(
                        CatalogDiscoveryAttributeName.SIZE, List.of("10"))),
                null,
                List.of()
        );

        FederatedCatalogDiscoveryResult result = federation(source).search(new CatalogDiscoveryRequest(
                "football boots",
                MERCHANT_ID,
                10,
                null,
                null,
                null,
                filters,
                null,
                Set.of()
        ));

        assertThat(source.supports(new CatalogDiscoveryRequest(
                "football boots",
                MERCHANT_ID,
                10,
                null,
                null,
                null,
                filters,
                null,
                Set.of()
        ))).isFalse();
        assertThat(result.status()).isEqualTo(CatalogDiscoveryTerminalStatus.FAILED);
        assertThat(result.sources()).isEmpty();
        assertThat(result.candidates()).isEmpty();
    }

    private MerchantSemanticCatalogDiscoverySource source(
            MerchantIntegrationLookupService integrationLookup
    ) {
        return source(integrationLookup, mock(MerchantCatalogSearchExecutor.class), false);
    }

    private MerchantSemanticCatalogDiscoverySource source(
            MerchantIntegrationLookupService integrationLookup,
            MerchantCatalogSearchExecutor catalogExecutor,
            boolean hasNextPage
    ) {
        MerchantSemanticSearchService semanticSearch = mock(MerchantSemanticSearchService.class);
        VoyageRerankClient rerankClient = mock(VoyageRerankClient.class);
        MerchantLookupService merchantLookup = mock(MerchantLookupService.class);
        MerchantProductDetailsEnricher detailsEnricher = mock(MerchantProductDetailsEnricher.class);
        MerchantProductFilterMatcher filterMatcher = mock(MerchantProductFilterMatcher.class);
        MerchantCatalogProductCandidate catalogCandidate = mock(MerchantCatalogProductCandidate.class);
        MerchantSemanticSearchResult merchant = merchant();
        MerchantSemanticProductResult preliminary = product("preliminary", "variant-preliminary");
        MerchantSemanticProductResult enriched = product("enriched", "variant-enriched");

        when(merchantLookup.activeSearchResult(MERCHANT_ID)).thenReturn(merchant);
        when(catalogCandidate.productKey()).thenReturn("merchant.example:product-1");
        when(catalogCandidate.rerankDocument()).thenReturn("Linen shirt");
        when(catalogExecutor.search(any(), any(), any(), any(), any(Integer.class), anyList()))
                .thenReturn(List.of(new MerchantCatalogSearchOutcome(
                        MerchantCatalogSearchAttemptResult.success(merchant, merchant.advertisedMcpEndpoint(), 1),
                        List.of(catalogCandidate),
                        hasNextPage
                )));
        when(filterMatcher.filterCatalogProducts(any(), any(), any(), anyList()))
                .thenReturn(List.of(catalogCandidate));
        when(rerankClient.rerank(any(), anyList())).thenReturn(List.of(new VoyageRerankResult(0, 1d)));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<MerchantSemanticProductResult> consumer = invocation.getArgument(2);
            consumer.accept(preliminary);
            return null;
        }).when(detailsEnricher).emitCatalogCandidates(anyList(), any(Integer.class), any());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<MerchantSemanticProductResult> consumer = invocation.getArgument(4);
            consumer.accept(enriched);
            return List.of(enriched);
        }).when(detailsEnricher).productResults(any(), anyList(), anyList(), any(), any());
        when(filterMatcher.filterProductResults(any(), any(), any(), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(3));

        MerchantSemanticProductSearchService searchService = new MerchantSemanticProductSearchService(
                semanticSearch,
                rerankClient,
                merchantLookup,
                new MerchantCatalogSearchProperties(1, 1, 1, 1),
                catalogExecutor,
                detailsEnricher,
                filterMatcher
        );
        MerchantCatalogDiscoveryEligibilityPolicy eligibilityPolicy =
                mock(MerchantCatalogDiscoveryEligibilityPolicy.class);
        when(eligibilityPolicy.eligible(anyList(), anySet(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserProductSearchHashService hashService = mock(UserProductSearchHashService.class);
        when(hashService.productKey(any())).thenReturn("merchant.example:product-1");
        return new MerchantSemanticCatalogDiscoverySource(
                searchService,
                eligibilityPolicy,
                integrationLookup,
                new UserCanonicalProductCandidateMapper(List.of()),
                hashService,
                new CatalogDiscoverySourceMetrics(new SimpleMeterRegistry()),
                new MerchantMcpToolProperties(100, 100, 500, Duration.ofMinutes(1))
        );
    }

    private FederatedCatalogDiscoveryService federation(MerchantSemanticCatalogDiscoverySource source) {
        return new FederatedCatalogDiscoveryService(
                List.of(source),
                new com.meant.api.module.catalog.properties.FederatedCatalogDiscoveryProperties(
                        Duration.ofSeconds(1)
                ),
                new FederatedCatalogDiscoveryMetrics(new SimpleMeterRegistry()),
                DEADLINE_SCHEDULER
        );
    }

    private MerchantSemanticSearchResult merchant() {
        return new MerchantSemanticSearchResult(
                MERCHANT_ID,
                "merchant.example",
                "Merchant",
                "https://merchant.example/mcp",
                null,
                "linen",
                1d,
                1d,
                1
        );
    }

    private MerchantSemanticProductResult product(String description, String variantId) {
        return new MerchantSemanticProductResult(
                MERCHANT_ID,
                "merchant.example",
                "Merchant",
                "https://merchant.example/mcp",
                1,
                1d,
                1d,
                "product-1",
                "Linen shirt",
                "<p>linen</p>",
                "https://merchant.example/products/1",
                "https://merchant.example/products/1.jpg",
                1000L,
                1000L,
                "USD",
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                null,
                description,
                null,
                List.of(),
                List.of(),
                "10.00",
                "10.00",
                "USD",
                1,
                false,
                List.of(),
                variantId,
                "Default",
                List.of(),
                "10.00",
                "USD",
                null,
                null,
                true,
                1,
                1d,
                1
        );
    }

    private MerchantIntegrationResult integration() {
        Instant now = Instant.parse("2026-07-11T00:00:00Z");
        return new MerchantIntegrationResult(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                MERCHANT_ID,
                MerchantIntegrationProvider.GENERIC_UCP,
                MerchantIntegrationKind.MERCHANT_CONNECTION,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG),
                "merchant-1",
                "merchant.example",
                null,
                "https://merchant.example/mcp",
                "2026-04-08",
                MerchantIntegrationAuthStrategy.NONE,
                MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.DISCOVERY,
                now,
                now,
                now
        );
    }
}
