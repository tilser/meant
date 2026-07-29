package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryCondition;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPrice;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPriceTier;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRating;
import com.meant.api.module.catalog.service.dto.CatalogSimilarityReference;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailure;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CatalogSourcePage;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.merchant.service.MerchantShopifyIdentityLookupService;
import com.meant.api.module.merchant.service.query.FindMerchantShopifyIdentityQuery;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.UserProductSearchQualificationPlanMapper;
import com.meant.api.module.user.service.UserProductSearchQualificationPlanResolver;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchPriceFilter;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogSearchRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ShopifyGlobalCatalogDiscoverySourceTest {

    private static final String VERIFIED_SHOP_ID = "gid://shopify/Shop/123";

    @Test
    void mapsTheCommonRequestAndCallsGlobalCatalogExactlyOnce() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("SHOPIFY"),
                ResultSourceType.PROVIDER_CATALOG,
                properties.sourceIdentity()
        );
        CatalogSourceResult providerResult = new CatalogSourceResult(
                source.provider(),
                source,
                CatalogSourceOperation.SEARCH,
                properties.protocolVersion(),
                NegotiatedCapabilities.none(),
                List.of(),
                null,
                false,
                null
        );
        FakeProvider provider = new FakeProvider(properties, source, providerResult);
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider, properties, authProperties(true));

        CatalogSourceResult result = adapter.search(new CatalogDiscoveryRequest(
                "linen shirt",
                null,
                12,
                new CatalogSearchContext("US", "CA", "90210", "en", "USD", "summer"),
                new CatalogSearchSignals("203.0.113.4", "Meant Test"),
                new CatalogSearchFilters(
                        List.of("gid://shopify/TaxonomyCategory/aa-8-1"),
                        new CatalogSearchPriceFilter(1000L, 5000L)
                )
        ), ignored -> { });

        assertThat(result.successful()).isTrue();
        assertThat(provider.calls).isEqualTo(1);
        assertThat(provider.request.query()).isEqualTo("linen shirt");
        assertThat(provider.request.limit()).isEqualTo(12);
        assertThat(provider.request.context().addressCountry()).isEqualTo("US");
        assertThat(provider.request.signals().buyerIp()).isEqualTo("203.0.113.4");
        assertThat(provider.request.signals().userAgent()).isEqualTo("Meant Test");
        assertThat(provider.request.filters().price().min()).isEqualTo(1000L);
        assertThat(provider.request.filters().categories())
                .containsExactly("gid://shopify/TaxonomyCategory/aa-8-1");
    }

    @Test
    void mapsEveryQualifiedDiscoveryFilterToTheShopifyExtension() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("SHOPIFY"),
                ResultSourceType.PROVIDER_CATALOG,
                properties.sourceIdentity()
        );
        CatalogSourceResult providerResult = new CatalogSourceResult(
                source.provider(), source, CatalogSourceOperation.SEARCH, properties.protocolVersion(),
                NegotiatedCapabilities.none(), List.of(), null, false, null);
        FakeProvider provider = new FakeProvider(properties, source, providerResult);
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider, properties, authProperties(true));

        adapter.search(new CatalogDiscoveryRequest(
                "trail running shoes",
                null,
                10,
                new CatalogSearchContext("US", null, null, "en", "USD", "marathon training"),
                null,
                null,
                new CatalogDiscoveryFilters(
                        true,
                        List.of(CatalogDiscoveryCondition.NEW, CatalogDiscoveryCondition.SECONDHAND),
                        new CatalogDiscoveryLocation("US", "CA", "90210"),
                        List.of(new CatalogDiscoveryLocation("CA", "ON", "M5V")),
                        new CatalogDiscoveryPrice(5000L, 15000L),
                        List.of("gid://shopify/Shop/123"),
                        List.of("gid://shopify/TaxonomyCategory/aa-8-1"),
                        List.of(
                                new CatalogDiscoveryAttributeFilter(
                                        CatalogDiscoveryAttributeName.COLOR, List.of("Black")),
                                new CatalogDiscoveryAttributeFilter(
                                        CatalogDiscoveryAttributeName.SIZE, List.of("10", "10.5")),
                                new CatalogDiscoveryAttributeFilter(
                                        CatalogDiscoveryAttributeName.TARGET_GENDER, List.of("Men"))
                        ),
                        new CatalogDiscoveryRating(new BigDecimal("4.5"), 10L),
                        List.of(CatalogDiscoveryPriceTier.LOW, CatalogDiscoveryPriceTier.MEDIUM)
                )
        ), ignored -> { });

        assertThat(provider.request.filters().available()).isTrue();
        assertThat(provider.request.filters().condition()).containsExactly("new", "secondhand");
        assertThat(provider.request.filters().shipsTo())
                .isEqualTo(new com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogFilters.Location(
                        "US", "CA", "90210"));
        assertThat(provider.request.filters().shipsFrom())
                .containsExactly(new com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogFilters.Location(
                        "CA", null, null));
        assertThat(provider.request.filters().price().min()).isEqualTo(5000L);
        assertThat(provider.request.filters().price().max()).isEqualTo(15000L);
        assertThat(provider.request.filters().shops()).containsExactly("gid://shopify/Shop/123");
        assertThat(provider.request.filters().categories())
                .containsExactly("gid://shopify/TaxonomyCategory/aa-8-1");
        assertThat(provider.request.filters().attributes())
                .extracting(com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogFilters.Attribute::name)
                .containsExactly("Color", "Size", "Target gender");
        assertThat(provider.request.filters().rating().variant().min()).isEqualByComparingTo("4.5");
        assertThat(provider.request.filters().rating().variant().minCount()).isEqualTo(10L);
        assertThat(provider.request.filters().priceTier()).containsExactly("low", "medium");
    }

    @Test
    void fetchesPastTheProviderPageLimitForCompleteDownstreamRankingInput() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        List<ProductCandidate> firstPageCandidates = IntStream.range(0, 50)
                .mapToObj(index -> candidate("offer-" + index))
                .toList();
        List<ProductCandidate> secondPageCandidates = IntStream.range(50, 100)
                .mapToObj(index -> candidate("offer-" + index))
                .toList();
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, firstPageCandidates, "cursor-50", true, false),
                successfulPage(source, secondPageCandidates, "cursor-end", false, false)
        );
        MerchantShopifyIdentityLookupService identities =
                mock(MerchantShopifyIdentityLookupService.class);
        UUID merchantId = UUID.randomUUID();
        when(identities.find(new FindMerchantShopifyIdentityQuery(merchantId)))
                .thenReturn(Optional.of("gid://shopify/Shop/123"));
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider,
                properties,
                authProperties(true),
                identities
        );
        CatalogSearchContext context =
                new CatalogSearchContext("US", "CA", "90210", "en", "USD", "running");
        CatalogSearchSignals signals = new CatalogSearchSignals("203.0.113.4", "Meant Test");
        CatalogDiscoveryFilters discoveryFilters = new CatalogDiscoveryFilters(
                true,
                List.of(CatalogDiscoveryCondition.NEW),
                new CatalogDiscoveryLocation("US", "CA", "90210"),
                List.of(new CatalogDiscoveryLocation("CA", null, null)),
                new CatalogDiscoveryPrice(5000L, 15000L),
                List.of("gid://shopify/Shop/999"),
                List.of("gid://shopify/TaxonomyCategory/aa-8-1"),
                List.of(new CatalogDiscoveryAttributeFilter(
                        CatalogDiscoveryAttributeName.SIZE,
                        List.of("10")
                )),
                new CatalogDiscoveryRating(new BigDecimal("4.5"), 10L),
                List.of(CatalogDiscoveryPriceTier.MEDIUM)
        );
        CatalogDiscoveryRequest request = new CatalogDiscoveryRequest(
                "trail running shoes",
                merchantId,
                100,
                context,
                signals,
                null,
                discoveryFilters,
                similarityReference("SHOPIFY", "gid://shopify/p/anchor-1"),
                Set.of()
        );
        List<ProductCandidate> emitted = new ArrayList<>();

        CatalogSourceResult result = adapter.search(request, emitted::add);

        assertThat(result.successful()).isTrue();
        assertThat(result.candidates()).hasSize(100);
        assertThat(emitted).containsExactlyElementsOf(result.candidates());
        assertThat(result.truncated()).isFalse();
        assertThat(provider.requests).hasSize(2);
        assertThat(provider.requests)
                .extracting(ShopifyGlobalCatalogSearchRequest::limit)
                .containsExactly(50, 50);
        assertThat(provider.requests)
                .extracting(ShopifyGlobalCatalogSearchRequest::cursor)
                .containsExactly(null, "cursor-50");
        ShopifyGlobalCatalogSearchRequest first = provider.requests.getFirst();
        ShopifyGlobalCatalogSearchRequest second = provider.requests.getLast();
        assertThat(second.query()).isEqualTo(first.query());
        assertThat(first.itemReference().id()).isEqualTo("gid://shopify/p/anchor-1");
        assertThat(second.itemReference()).isEqualTo(first.itemReference());
        assertThat(second.context()).isEqualTo(first.context());
        assertThat(second.signals()).isEqualTo(first.signals());
        assertThat(second.filters()).isEqualTo(first.filters());
        assertThat(first.filters().shops()).containsExactly("gid://shopify/Shop/123");
        assertThat(second.filters().shops()).containsExactly("gid://shopify/Shop/123");
    }

    @Test
    void deduplicatesInFirstSeenOrderAndStopsOnARepeatedCursor() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        ProductCandidate first = candidate("offer-1");
        ProductCandidate duplicate = candidate("offer-2");
        ProductCandidate last = candidate("offer-3");
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, List.of(first, duplicate), "cursor-1", true, false),
                successfulPage(source, List.of(duplicate, last), "cursor-1", true, false)
        );
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider, properties, authProperties(true));

        CatalogSourceResult result = adapter.search(
                new CatalogDiscoveryRequest("shoes", null, 10, null, null, null),
                ignored -> { }
        );

        assertThat(result.successful()).isTrue();
        assertThat(result.candidates()).containsExactly(first, duplicate, last);
        assertThat(result.truncated()).isTrue();
        assertThat(result.page().cursor()).isEqualTo("cursor-1");
        assertThat(provider.requests)
                .extracting(ShopifyGlobalCatalogSearchRequest::cursor)
                .containsExactly(null, "cursor-1");
    }

    @Test
    void continuesPastADuplicateOnlyPageWhenTheCursorAdvances() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        ProductCandidate first = candidate("offer-1");
        ProductCandidate duplicate = candidate("offer-1");
        ProductCandidate last = candidate("offer-2");
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, List.of(first), "cursor-1", true, false),
                successfulPage(source, List.of(duplicate), "cursor-2", true, false),
                successfulPage(source, List.of(last), "cursor-end", false, false)
        );
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider, properties, authProperties(true));

        CatalogSourceResult result = adapter.search(
                new CatalogDiscoveryRequest("shoes", null, 10, null, null, null),
                ignored -> { }
        );

        assertThat(result.successful()).isTrue();
        assertThat(result.candidates()).containsExactly(first, last);
        assertThat(result.truncated()).isFalse();
        assertThat(provider.requests)
                .extracting(ShopifyGlobalCatalogSearchRequest::cursor)
                .containsExactly(null, "cursor-1", "cursor-2");
    }

    @Test
    void stopsAtTheConfiguredPageBoundWhenDuplicateOffersKeepAdvancingTheCursor() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        ProductCandidate first = candidate("offer-1");
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, List.of(first), "cursor-1", true, false),
                successfulPage(source, List.of(candidate("offer-1")), "cursor-2", true, false),
                successfulPage(source, List.of(candidate("offer-1")), "cursor-3", true, false),
                successfulPage(source, List.of(candidate("offer-1")), "cursor-4", true, false)
        );
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider, properties, authProperties(true));

        CatalogSourceResult result = adapter.search(
                new CatalogDiscoveryRequest("shoes", null, 100, null, null, null),
                ignored -> { }
        );

        assertThat(properties.maximumSearchPages()).isEqualTo(4);
        assertThat(result.successful()).isTrue();
        assertThat(result.candidates()).containsExactly(first);
        assertThat(result.truncated()).isTrue();
        assertThat(result.page().cursor()).isEqualTo("cursor-4");
        assertThat(provider.calls).isEqualTo(4);
        assertThat(provider.requests)
                .extracting(ShopifyGlobalCatalogSearchRequest::cursor)
                .containsExactly(null, "cursor-1", "cursor-2", "cursor-3");
    }

    @Test
    void stopsAsTruncatedWhenShopifyClaimsAnotherPageWithoutACursor() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        ProductCandidate candidate = candidate("offer-1");
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, List.of(candidate), null, true, false)
        );
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider, properties, authProperties(true));

        CatalogSourceResult result = adapter.search(
                new CatalogDiscoveryRequest("shoes", null, 10, null, null, null),
                ignored -> { }
        );

        assertThat(result.successful()).isTrue();
        assertThat(result.candidates()).containsExactly(candidate);
        assertThat(result.truncated()).isTrue();
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void stopsOnProviderTruncationWithoutFollowingTheCursor() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        ProductCandidate candidate = candidate("offer-1");
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, List.of(candidate), "cursor-1", true, true)
        );
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider, properties, authProperties(true));

        CatalogSourceResult result = adapter.search(
                new CatalogDiscoveryRequest("shoes", null, 10, null, null, null),
                ignored -> { }
        );

        assertThat(result.successful()).isTrue();
        assertThat(result.candidates()).containsExactly(candidate);
        assertThat(result.truncated()).isTrue();
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void laterPageFailureDoesNotExposeFirstPageCandidates() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        ProductCandidate candidate = candidate("offer-1");
        CatalogSourceResult failure = failedPage(
                source,
                CatalogSourceFailureKind.TRANSIENT_UPSTREAM,
                "Second page failed"
        );
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, List.of(candidate), "cursor-1", true, false),
                failure
        );
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider, properties, authProperties(true));
        List<ProductCandidate> emitted = new ArrayList<>();

        CatalogSourceResult result = adapter.search(
                new CatalogDiscoveryRequest("shoes", null, 10, null, null, null),
                emitted::add
        );

        assertThat(result).isSameAs(failure);
        assertThat(result.successful()).isFalse();
        assertThat(result.candidates()).isEmpty();
        assertThat(emitted).isEmpty();
        assertThat(provider.calls).isEqualTo(2);
    }

    @Test
    void sourceDeadlineDoesNotExposeBufferedCandidates() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        AtomicLong nanoTime = new AtomicLong();
        FakeProvider provider = new FakeProvider(
                properties,
                source,
                List.of(successfulPage(
                        source,
                        List.of(candidate("offer-1")),
                        "cursor-1",
                        true,
                        false
                )),
                () -> nanoTime.set(properties.discoverySourceTimeout().toNanos())
        );
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider,
                properties,
                authProperties(true),
                null,
                nanoTime::get
        );
        List<ProductCandidate> emitted = new ArrayList<>();

        CatalogSourceResult result = adapter.search(
                new CatalogDiscoveryRequest("shoes", null, 10, null, null, null),
                emitted::add
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().kind()).isEqualTo(CatalogSourceFailureKind.TIMEOUT);
        assertThat(result.candidates()).isEmpty();
        assertThat(emitted).isEmpty();
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void statefulSizeAndDestinationAnswersReachOneShopifySearchWithoutLosingTheRequest() {
        var resolver = new UserProductSearchQualificationPlanResolver();
        var first = resolver.safeFallback(qualificationQuery(
                "running shoes",
                "running shoes",
                null
        ));
        var second = resolver.safeFallback(qualificationQuery(
                "running shoes",
                "46",
                first
        ));
        var ready = resolver.safeFallback(qualificationQuery(
                "running shoes",
                "US",
                second
        ));

        assertThat(first.questionTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        assertThat(second.questionTargets()).containsExactly(UserProductSearchQuestionTarget.SHIPS_TO);
        assertThat(ready.missingTargets()).isEmpty();

        CatalogDiscoveryFilters mapped = new UserProductSearchQualificationPlanMapper().map(ready);
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("SHOPIFY"),
                ResultSourceType.PROVIDER_CATALOG,
                properties.sourceIdentity()
        );
        CatalogSourceResult providerResult = new CatalogSourceResult(
                source.provider(), source, CatalogSourceOperation.SEARCH, properties.protocolVersion(),
                NegotiatedCapabilities.none(), List.of(), null, false, null);
        FakeProvider provider = new FakeProvider(properties, source, providerResult);
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider, properties, authProperties(true));

        adapter.search(new CatalogDiscoveryRequest(
                ready.effectiveQuery(),
                null,
                10,
                null,
                null,
                null,
                mapped
        ), ignored -> { });

        assertThat(provider.calls).isEqualTo(1);
        assertThat(provider.request.query()).isEqualTo("running shoes");
        assertThat(provider.request.filters().available()).isTrue();
        assertThat(provider.request.filters().shipsTo().country()).isEqualTo("US");
        assertThat(provider.request.filters().attributes())
                .singleElement()
                .satisfies(attribute -> {
                    assertThat(attribute.name()).isEqualTo("Size");
                    assertThat(attribute.values()).containsExactly("46");
                });
    }

    @Test
    void scopedRequestsRemainEligibleWithoutResolvingMerchantIdentity() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        MerchantShopifyIdentityLookupService identities = mock(
                MerchantShopifyIdentityLookupService.class,
                invocation -> {
                    throw new AssertionError("supports must not resolve merchant identity");
                }
        );
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                new FakeProvider(properties, source, null),
                properties,
                authProperties(true),
                identities
        );
        CatalogDiscoveryRequest request = new CatalogDiscoveryRequest(
                "linen shirt",
                UUID.randomUUID(),
                10,
                null,
                null,
                null,
                null,
                similarityReference("SHOPIFY", "gid://shopify/p/anchor-1"),
                Set.of()
        );

        assertThat(adapter.supports(request)).isTrue();
        verifyNoInteractions(identities);
    }

    @Test
    void missingVerifiedMerchantIdentityFailsClosedWithoutCallingShopify() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        FakeProvider provider = new FakeProvider(properties, source, null);
        MerchantShopifyIdentityLookupService identities =
                mock(MerchantShopifyIdentityLookupService.class);
        UUID merchantId = UUID.randomUUID();
        when(identities.find(new FindMerchantShopifyIdentityQuery(merchantId)))
                .thenReturn(Optional.empty());
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider,
                properties,
                authProperties(true),
                identities
        );
        List<ProductCandidate> emitted = new ArrayList<>();

        CatalogSourceResult result = adapter.search(new CatalogDiscoveryRequest(
                "linen shirt",
                merchantId,
                10,
                null,
                null,
                null
        ), emitted::add);

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().kind()).isEqualTo(CatalogSourceFailureKind.UNAVAILABLE);
        assertThat(result.candidates()).isEmpty();
        assertThat(emitted).isEmpty();
        assertThat(provider.calls).isZero();
    }

    @Test
    void merchantIdentityLookupFailureFailsClosedWithoutCallingShopify() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        FakeProvider provider = new FakeProvider(properties, source, null);
        MerchantShopifyIdentityLookupService identities =
                mock(MerchantShopifyIdentityLookupService.class);
        UUID merchantId = UUID.randomUUID();
        when(identities.find(new FindMerchantShopifyIdentityQuery(merchantId)))
                .thenThrow(new IllegalStateException("Identity repository unavailable"));
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider,
                properties,
                authProperties(true),
                identities
        );
        List<ProductCandidate> emitted = new ArrayList<>();

        CatalogSourceResult result = adapter.search(
                scopedRequest(merchantId, null),
                emitted::add
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().kind()).isEqualTo(CatalogSourceFailureKind.UNAVAILABLE);
        assertThat(result.candidates()).isEmpty();
        assertThat(emitted).isEmpty();
        assertThat(provider.calls).isZero();
    }

    @Test
    void nonCanonicalCandidateMerchantIdentityFailsClosed() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        String nonCanonicalShopId = "gid://shopify/shop/123";
        ProductCandidate candidate = scopedCandidate(
                "offer-1",
                nonCanonicalShopId,
                List.of(nonCanonicalShopId),
                List.of(nonCanonicalShopId)
        );
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, List.of(candidate), "cursor-end", false, false)
        );
        UUID merchantId = UUID.randomUUID();
        ShopifyGlobalCatalogDiscoverySource adapter = scopedAdapter(
                provider,
                properties,
                merchantId,
                VERIFIED_SHOP_ID
        );
        List<ProductCandidate> emitted = new ArrayList<>();

        CatalogSourceResult result = adapter.search(
                scopedRequest(merchantId, null),
                emitted::add
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().kind()).isEqualTo(CatalogSourceFailureKind.MALFORMED_RESPONSE);
        assertThat(result.candidates()).isEmpty();
        assertThat(emitted).isEmpty();
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void ambiguousCandidateMerchantProvenanceFailsClosed() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        ProductCandidate candidate = scopedCandidate(
                "offer-1",
                VERIFIED_SHOP_ID,
                List.of(VERIFIED_SHOP_ID, "gid://shopify/Shop/999"),
                List.of(VERIFIED_SHOP_ID)
        );
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, List.of(candidate), "cursor-end", false, false)
        );
        UUID merchantId = UUID.randomUUID();
        ShopifyGlobalCatalogDiscoverySource adapter = scopedAdapter(
                provider,
                properties,
                merchantId,
                VERIFIED_SHOP_ID
        );
        List<ProductCandidate> emitted = new ArrayList<>();

        CatalogSourceResult result = adapter.search(
                scopedRequest(merchantId, null),
                emitted::add
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().kind()).isEqualTo(CatalogSourceFailureKind.MALFORMED_RESPONSE);
        assertThat(result.candidates()).isEmpty();
        assertThat(emitted).isEmpty();
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void missingCandidateMerchantIdentityFailsClosed() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        ProductCandidate candidate = scopedCandidate(
                "offer-1",
                null,
                List.of(VERIFIED_SHOP_ID),
                List.of(VERIFIED_SHOP_ID)
        );
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, List.of(candidate), "cursor-end", false, false)
        );
        UUID merchantId = UUID.randomUUID();
        ShopifyGlobalCatalogDiscoverySource adapter = scopedAdapter(
                provider,
                properties,
                merchantId,
                VERIFIED_SHOP_ID
        );
        List<ProductCandidate> emitted = new ArrayList<>();

        CatalogSourceResult result = adapter.search(
                scopedRequest(merchantId, null),
                emitted::add
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().kind()).isEqualTo(CatalogSourceFailureKind.MALFORMED_RESPONSE);
        assertThat(result.candidates()).isEmpty();
        assertThat(emitted).isEmpty();
        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void laterPageMerchantMismatchDoesNotExposeBufferedSimilarityCandidates() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = source(properties);
        ProductCandidate matching = candidate("offer-1");
        ProductCandidate mismatched = scopedCandidate(
                "offer-2",
                "gid://shopify/Shop/999",
                List.of("gid://shopify/Shop/999"),
                List.of("gid://shopify/Shop/999")
        );
        FakeProvider provider = FakeProvider.pages(
                properties,
                source,
                successfulPage(source, List.of(matching), "cursor-1", true, false),
                successfulPage(source, List.of(mismatched), "cursor-end", false, false)
        );
        UUID merchantId = UUID.randomUUID();
        ShopifyGlobalCatalogDiscoverySource adapter = scopedAdapter(
                provider,
                properties,
                merchantId,
                VERIFIED_SHOP_ID
        );
        List<ProductCandidate> emitted = new ArrayList<>();

        CatalogSourceResult result = adapter.search(
                scopedRequest(
                        merchantId,
                        similarityReference("SHOPIFY", "gid://shopify/p/anchor-1")
                ),
                emitted::add
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.failure().kind()).isEqualTo(CatalogSourceFailureKind.MALFORMED_RESPONSE);
        assertThat(result.candidates()).isEmpty();
        assertThat(emitted).isEmpty();
        assertThat(provider.calls).isEqualTo(2);
    }

    @Test
    void verifiedShopifyMerchantScopeRunsGlobalSearchWithATrustedShopFilter() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("SHOPIFY"),
                ResultSourceType.PROVIDER_CATALOG,
                properties.sourceIdentity()
        );
        CatalogSourceResult providerResult = new CatalogSourceResult(
                source.provider(), source, CatalogSourceOperation.SEARCH, properties.protocolVersion(),
                NegotiatedCapabilities.none(), List.of(), null, false, null);
        FakeProvider provider = new FakeProvider(properties, source, providerResult);
        MerchantShopifyIdentityLookupService identities =
                mock(MerchantShopifyIdentityLookupService.class);
        UUID merchantId = UUID.randomUUID();
        when(identities.find(new FindMerchantShopifyIdentityQuery(merchantId)))
                .thenReturn(Optional.of("gid://shopify/Shop/123"));
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider,
                properties,
                authProperties(true),
                identities
        );
        CatalogDiscoveryRequest request = new CatalogDiscoveryRequest(
                "running shoes",
                merchantId,
                10,
                null,
                null,
                null,
                new CatalogDiscoveryFilters(
                        true,
                        List.of(),
                        new CatalogDiscoveryLocation("US", null, null),
                        List.of(),
                        null,
                        List.of("gid://shopify/Shop/999"),
                        List.of(),
                        List.of(new CatalogDiscoveryAttributeFilter(
                                CatalogDiscoveryAttributeName.SIZE,
                                List.of("46")
                        )),
                        null,
                        List.of()
                )
        );

        assertThat(adapter.supports(request)).isTrue();
        adapter.search(request, ignored -> { });

        assertThat(provider.request.filters().shops())
                .containsExactly("gid://shopify/Shop/123");
        assertThat(provider.request.filters().shipsTo().country()).isEqualTo("US");
        assertThat(provider.request.filters().attributes().getFirst().values())
                .containsExactly("46");
    }

    @Test
    void mapsMatchingBroadSimilarityAndDeclinesReferencesOwnedByAnotherProvider() {
        ShopifyGlobalCatalogProperties properties = properties();
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("SHOPIFY"),
                ResultSourceType.PROVIDER_CATALOG,
                properties.sourceIdentity()
        );
        CatalogSourceResult providerResult = new CatalogSourceResult(
                source.provider(),
                source,
                CatalogSourceOperation.SEARCH,
                properties.protocolVersion(),
                NegotiatedCapabilities.none(),
                List.of(),
                null,
                false,
                null
        );
        FakeProvider provider = new FakeProvider(properties, source, providerResult);
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                provider, properties, authProperties(true));
        CatalogSimilarityReference matchingReference = similarityReference(
                "SHOPIFY", "gid://shopify/p/anchor-1");
        CatalogDiscoveryRequest request = new CatalogDiscoveryRequest(
                "linen shirt", null, 10, null, null, null, matchingReference);

        assertThat(adapter.supports(request)).isTrue();
        adapter.search(request, ignored -> { });

        assertThat(provider.calls).isEqualTo(1);
        assertThat(provider.request.query()).isEqualTo("linen shirt");
        assertThat(provider.request.itemReference().id()).isEqualTo("gid://shopify/p/anchor-1");
        assertThat(adapter.supports(new CatalogDiscoveryRequest(
                "linen shirt",
                null,
                10,
                null,
                null,
                null,
                similarityReference("OTHER", "gid://other/Product/anchor-1")
        ))).isFalse();
    }

    @Test
    void authEnabledDiscoveryDisabledDoesNotScheduleGlobalCatalog() {
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                new FakeProvider(properties(false), null, null),
                properties(false),
                authProperties(true)
        );

        assertThat(adapter.supports(broadRequest())).isFalse();
    }

    @Test
    void discoveryEnabledAuthDisabledSchedulesKeylessGlobalCatalog() {
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                new FakeProvider(properties(true), null, null),
                properties(true),
                authProperties(false)
        );

        assertThat(adapter.supports(broadRequest())).isTrue();
    }

    @Test
    void discoveryAndAuthEnabledScheduleGlobalCatalog() {
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                new FakeProvider(properties(true), null, null),
                properties(true),
                authProperties(true)
        );

        assertThat(adapter.supports(broadRequest())).isTrue();
    }

    @Test
    void sourceRemainsEligibleWhenRuntimeDiscoveryIsDisabled() {
        ShopifyGlobalCatalogProperties properties = properties(true, false);
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                new FakeProvider(properties, null, null),
                properties,
                authProperties(false)
        );

        assertThat(adapter.supports(broadRequest())).isTrue();
    }

    @Test
    void liveRuntimeDiscoverySourceTimeoutContainsDiscoveryCatalogAndSchedulerReserve() {
        ShopifyGlobalCatalogProperties properties = properties(true, true);
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                new FakeProvider(properties, null, null),
                properties,
                authProperties(false)
        );

        assertThat(adapter.timeout()).isEqualTo(Duration.ofSeconds(51));
    }

    @Test
    void pinnedRouteSourceTimeoutContainsCatalogAndSchedulerReserve() {
        ShopifyGlobalCatalogProperties properties = properties(true, false);
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                new FakeProvider(properties, null, null),
                properties,
                authProperties(false)
        );

        assertThat(adapter.timeout()).isEqualTo(Duration.ofSeconds(41));
    }

    private ShopifyGlobalCatalogProperties properties() {
        return properties(true);
    }

    private ShopifyGlobalCatalogProperties properties(boolean discoveryEnabled) {
        return properties(discoveryEnabled, true);
    }

    private ShopifyGlobalCatalogProperties properties(
            boolean discoveryEnabled,
            boolean runtimeDiscoveryEnabled
    ) {
        return new ShopifyGlobalCatalogProperties(
                discoveryEnabled,
                runtimeDiscoveryEnabled,
                java.net.URI.create("https://catalog.shopify.test/api/ucp/mcp"),
                Set.of("catalog.shopify.test"),
                "2026-04-08",
                Duration.ofMinutes(15),
                10,
                50,
                50,
                200,
                16,
                Duration.ofSeconds(2),
                Duration.ofSeconds(8),
                Duration.ofSeconds(10),
                3,
                Duration.ofSeconds(30)
        );
    }

    private DiscoverySourceIdentity source(ShopifyGlobalCatalogProperties properties) {
        return new DiscoverySourceIdentity(
                new ProviderIdentity("SHOPIFY"),
                ResultSourceType.PROVIDER_CATALOG,
                properties.sourceIdentity()
        );
    }

    private CatalogSourceResult successfulPage(
            DiscoverySourceIdentity source,
            List<ProductCandidate> candidates,
            String cursor,
            boolean hasNextPage,
            boolean truncated
    ) {
        return new CatalogSourceResult(
                source.provider(),
                source,
                CatalogSourceOperation.SEARCH,
                "2026-04-08",
                NegotiatedCapabilities.none(),
                candidates,
                new CatalogSourcePage(cursor, hasNextPage, null),
                truncated,
                null
        );
    }

    private CatalogSourceResult failedPage(
            DiscoverySourceIdentity source,
            CatalogSourceFailureKind kind,
            String message
    ) {
        return new CatalogSourceResult(
                source.provider(),
                source,
                CatalogSourceOperation.SEARCH,
                "2026-04-08",
                NegotiatedCapabilities.none(),
                List.of(),
                null,
                false,
                new CatalogSourceFailure(kind, message, null, null)
        );
    }

    private ProductCandidate candidate(String offerKey) {
        return scopedCandidate(
                offerKey,
                VERIFIED_SHOP_ID,
                List.of(VERIFIED_SHOP_ID),
                List.of(VERIFIED_SHOP_ID)
        );
    }

    private ProductCandidate scopedCandidate(
            String offerKey,
            String identityShopId,
            List<String> candidateProvenanceShopIds,
            List<String> offerProvenanceShopIds
    ) {
        ProductCandidate candidate = mock(ProductCandidate.class);
        Offer offer = mock(Offer.class);
        OfferIdentity identity = mock(OfferIdentity.class);
        when(candidate.offer()).thenReturn(offer);
        when(offer.key()).thenReturn(offerKey);
        when(offer.identity()).thenReturn(identity);
        when(identity.merchantScope()).thenReturn(identityShopId == null
                ? OfferMerchantScope.localIntegrationFallback(UUID.randomUUID())
                : OfferMerchantScope.external(merchantIdentifier(identityShopId)));
        List<ResultProvenance> candidateProvenance = candidateProvenanceShopIds.stream()
                .map(this::provenance)
                .toList();
        List<ResultProvenance> offerProvenance = offerProvenanceShopIds.stream()
                .map(this::provenance)
                .toList();
        when(candidate.provenance()).thenReturn(candidateProvenance);
        when(offer.provenance()).thenReturn(offerProvenance);
        return candidate;
    }

    private ResultProvenance provenance(String shopId) {
        ResultProvenance provenance = mock(ResultProvenance.class);
        when(provenance.externalMerchantReference()).thenReturn(merchantIdentifier(shopId));
        return provenance;
    }

    private ExternalIdentifier merchantIdentifier(String shopId) {
        return new ExternalIdentifier(
                ExternalIdentifierType.MERCHANT,
                "SHOPIFY",
                shopId
        );
    }

    private ShopifyGlobalCatalogDiscoverySource scopedAdapter(
            FakeProvider provider,
            ShopifyGlobalCatalogProperties properties,
            UUID merchantId,
            String shopId
    ) {
        MerchantShopifyIdentityLookupService identities =
                mock(MerchantShopifyIdentityLookupService.class);
        when(identities.find(new FindMerchantShopifyIdentityQuery(merchantId)))
                .thenReturn(Optional.of(shopId));
        return new ShopifyGlobalCatalogDiscoverySource(
                provider,
                properties,
                authProperties(true),
                identities
        );
    }

    private CatalogDiscoveryRequest scopedRequest(
            UUID merchantId,
            CatalogSimilarityReference similarityReference
    ) {
        return new CatalogDiscoveryRequest(
                "running shoes",
                merchantId,
                10,
                null,
                null,
                null,
                null,
                similarityReference,
                Set.of()
        );
    }

    private CatalogDiscoveryRequest broadRequest() {
        return new CatalogDiscoveryRequest("linen shirt", null, 10, null, null, null);
    }

    private GenerateUserProductSearchQualificationQuery qualificationQuery(
            String originalQuery,
            String message,
            com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan previous
    ) {
        Instant now = Instant.parse("2026-07-29T12:00:00Z");
        return new GenerateUserProductSearchQualificationQuery(
                originalQuery,
                message,
                previous,
                new UserSettingsResult(
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        now,
                        now
                ),
                List.of()
        );
    }

    private CatalogSimilarityReference similarityReference(String provider, String productReference) {
        ProviderIdentity providerIdentity = new ProviderIdentity(provider);
        return new CatalogSimilarityReference(
                providerIdentity,
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        providerIdentity.value(),
                        productReference
                )
        );
    }

    private ShopifyAgentAuthProperties authProperties(boolean enabled) {
        return new ShopifyAgentAuthProperties(
                enabled,
                "test",
                enabled ? "client" : "",
                enabled ? "secret" : "",
                java.net.URI.create("https://api.shopify.test/auth/access_token"),
                Duration.ofMinutes(5),
                Duration.ofHours(1)
        );
    }

    private static final class FakeProvider extends ShopifyGlobalCatalogProvider {

        private final DiscoverySourceIdentity source;
        private final List<CatalogSourceResult> results;
        private final Runnable afterCall;
        private final List<ShopifyGlobalCatalogSearchRequest> requests = new ArrayList<>();
        private ShopifyGlobalCatalogSearchRequest request;
        private int calls;

        private FakeProvider(
                ShopifyGlobalCatalogProperties properties,
                DiscoverySourceIdentity source,
                CatalogSourceResult result
        ) {
            this(
                    properties,
                    source,
                    result == null ? List.of() : List.of(result),
                    () -> { }
            );
        }

        private FakeProvider(
                ShopifyGlobalCatalogProperties properties,
                DiscoverySourceIdentity source,
                List<CatalogSourceResult> results,
                Runnable afterCall
        ) {
            super(null, null, null, null, properties);
            this.source = source;
            this.results = List.copyOf(results);
            this.afterCall = afterCall;
        }

        private static FakeProvider pages(
                ShopifyGlobalCatalogProperties properties,
                DiscoverySourceIdentity source,
                CatalogSourceResult... results
        ) {
            return new FakeProvider(properties, source, List.of(results), () -> { });
        }

        @Override
        public DiscoverySourceIdentity discoverySourceIdentity() {
            return source;
        }

        @Override
        public CatalogSourceResult searchCatalog(ShopifyGlobalCatalogSearchRequest request) {
            this.request = request;
            requests.add(request);
            if (calls >= results.size()) {
                throw new AssertionError("Unexpected Shopify catalog page request");
            }
            CatalogSourceResult result = results.get(calls);
            calls++;
            afterCall.run();
            return result;
        }
    }
}
