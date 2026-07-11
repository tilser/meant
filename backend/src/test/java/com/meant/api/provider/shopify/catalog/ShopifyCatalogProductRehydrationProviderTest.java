package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CommercialFact;
import com.meant.api.module.catalog.service.dto.CommercialFreshnessStatus;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogLookupRequest;
import com.meant.api.module.catalog.service.CommercialFreshnessPolicy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ShopifyCatalogProductRehydrationProviderTest {
    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private static final ProviderIdentity SHOPIFY = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            SHOPIFY,
            ResultSourceType.PROVIDER_CATALOG,
            "SHOPIFY_GLOBAL_CATALOG"
    );

    @Test
    void matchesExactSellerForSameProductAndVariantUnderBothCandidateOrders() {
        for (List<String> candidateOrder : List.of(List.of("seller-a", "seller-b"), List.of("seller-b", "seller-a"))) {
            for (List<String> requestedOrder : List.of(
                    List.of("seller-a", "seller-b"),
                    List.of("seller-b", "seller-a"))) {
                ShopifyGlobalCatalogProvider global = providerSource(50);
                CatalogSourceResult sourceResult = successful(candidateOrder.stream()
                        .map(seller -> candidate("product-1", "variant-1", seller,
                                seller.equals("seller-a") ? 1000 : 2500, available()))
                        .toList());
                when(global.lookupCatalog(any())).thenReturn(sourceResult);
                ShopifyCatalogProductRehydrationProvider provider = rehydrator(global, 50);

                var results = provider.rehydrate(requestedOrder.stream()
                                .map(seller -> reference(
                                        "saved-" + seller, "product-1", "variant-1", seller, List.of()))
                                .toList(),
                        new CatalogRehydrationContext("CZ", "en"));

                assertThat(results).allSatisfy(result -> {
                    String seller = result.resolvedReference().externalMerchantReference().value();
                    assertThat(result.facts().price().minorUnits())
                            .isEqualTo(seller.equals("seller-a") ? 1000L : 2500L);
                });
            }
        }
    }

    @Test
    void rejectsWrongMerchantVariantOptionsAndClientRouting() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        CatalogSourceResult sourceResult = successful(List.of(candidate(
                "product-1",
                "variant-1",
                "seller-a",
                1000,
                available()
        )));
        when(global.lookupCatalog(any())).thenReturn(sourceResult);
        ShopifyCatalogProductRehydrationProvider provider = rehydrator(global, 50);
        ProductAttribute size = new ProductAttribute("variant-option", "Size", "M");

        var results = provider.rehydrate(List.of(
                reference("merchant", "product-1", "variant-1", "seller-b", List.of()),
                reference("variant", "product-1", "variant-2", "seller-a", List.of()),
                reference("options", "product-1", "variant-1", "seller-a", List.of(size)),
                new CatalogProductReference(
                        "routing",
                        SOURCE,
                        java.util.UUID.randomUUID(),
                        null,
                        merchant("seller-a"),
                        product("product-1"),
                        variant("variant-1"),
                        List.of()
                )
        ), new CatalogRehydrationContext(null, null));

        assertThat(results).extracting(result -> result.status())
                .containsOnly(CatalogRehydrationStatus.UNAVAILABLE);
        assertThat(results.get(3).failure()).isEqualTo(CatalogRehydrationFailureKind.INVALID_REFERENCE);
    }

    @Test
    void isolatesFailedLookupChunkFromSuccessfulChunk() {
        ShopifyGlobalCatalogProvider global = providerSource(1);
        when(global.lookupCatalog(any())).thenAnswer(invocation -> {
            ShopifyGlobalCatalogLookupRequest request = invocation.getArgument(0);
            return request.ids().contains("product-ok")
                    ? successful(List.of(candidate(
                            "product-ok", "variant-ok", "seller", 1200, available())))
                    : throwFailure();
        });
        ShopifyCatalogProductRehydrationProvider provider = rehydrator(global, 1);

        var results = provider.rehydrate(List.of(
                reference("ok", "product-ok", "variant-ok", "seller", List.of()),
                reference("failed", "product-failed", "variant-failed", "seller", List.of())
        ), new CatalogRehydrationContext(null, null));

        assertThat(results.getFirst().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(results.getLast().status()).isEqualTo(CatalogRehydrationStatus.DEGRADED);
        assertThat(results.getLast().failure()).isEqualTo(CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void unknownAvailabilityDoesNotReceiveCurrentFreshness() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        CatalogSourceResult sourceResult = successful(List.of(candidate(
                "product-1", "variant-1", "seller", 1000, OfferAvailability.unknown())));
        when(global.lookupCatalog(any())).thenReturn(sourceResult);

        var result = rehydrator(global, 50).rehydrate(
                List.of(reference("saved", "product-1", "variant-1", "seller", List.of())),
                new CatalogRehydrationContext(null, null)
        ).getFirst();

        assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.facts().purchaseFreshness().availability()).isNull();
        assertThat(new CommercialFreshnessPolicy().decide(
                result.facts().purchaseFreshness(),
                EnumSet.of(CommercialFact.AVAILABILITY),
                NOW
        ).status()).isEqualTo(CommercialFreshnessStatus.REFRESH_REQUIRED);
    }

    @Test
    void propagatesAvailableCountryAndLanguageToShopifyLookup() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        CatalogSourceResult lookupResult = successful(List.of(candidate(
                "product-1", "variant-1", "seller", 1000, available())));
        when(global.lookupCatalog(any())).thenReturn(lookupResult);

        rehydrator(global, 50).rehydrate(
                List.of(reference("saved", "product-1", "variant-1", "seller", List.of())),
                new CatalogRehydrationContext("CZ", "cs")
        );

        ArgumentCaptor<ShopifyGlobalCatalogLookupRequest> request =
                ArgumentCaptor.forClass(ShopifyGlobalCatalogLookupRequest.class);
        org.mockito.Mockito.verify(global).lookupCatalog(request.capture());
        assertThat(request.getValue().context().addressCountry()).isEqualTo("CZ");
        assertThat(request.getValue().context().language()).isEqualTo("cs");
    }

    private ShopifyCatalogProductRehydrationProvider rehydrator(
            ShopifyGlobalCatalogProvider provider,
            int maximumLookupIds
    ) {
        ShopifyGlobalCatalogProperties properties = mock(ShopifyGlobalCatalogProperties.class);
        when(properties.maximumLookupIds()).thenReturn(maximumLookupIds);
        return new ShopifyCatalogProductRehydrationProvider(
                provider,
                properties,
                new ShopifyCatalogDataUseProperties(false, Duration.ofMinutes(15), Duration.ofMinutes(2)),
                new ShopifyCatalogReferenceMatcher(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ShopifyGlobalCatalogProvider providerSource(int ignored) {
        ShopifyGlobalCatalogProvider provider = mock(ShopifyGlobalCatalogProvider.class);
        when(provider.discoverySourceIdentity()).thenReturn(SOURCE);
        return provider;
    }

    private CatalogSourceResult successful(List<ProductCandidate> candidates) {
        CatalogSourceResult result = mock(CatalogSourceResult.class);
        when(result.successful()).thenReturn(true);
        when(result.candidates()).thenReturn(candidates);
        return result;
    }

    private OfferAvailability available() {
        return new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null);
    }

    private CatalogSourceResult throwFailure() {
        throw new IllegalStateException("failed chunk");
    }

    private ProductCandidate candidate(
            String productId,
            String variantId,
            String seller,
            long price,
            OfferAvailability availability
    ) {
        ExternalIdentifier merchant = merchant(seller);
        ExternalIdentifier product = product(productId);
        ExternalIdentifier variant = variant(variantId);
        ResultProvenance provenance = new ResultProvenance(
                SHOPIFY,
                SOURCE,
                null,
                merchant,
                product,
                variant,
                new ResultFreshness(NOW, null),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "test", URI.create("https://catalog.test"))
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        SHOPIFY,
                        OfferMerchantScope.external(merchant),
                        product,
                        variant,
                        List.of(),
                        List.of(),
                        null
                ),
                seller,
                variantId,
                new Money(price, "USD"),
                null,
                availability,
                List.of(),
                null,
                List.of(provenance)
        );
        return new ProductCandidate(
                "Current product",
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

    private CatalogProductReference reference(
            String key,
            String product,
            String variant,
            String merchant,
            List<ProductAttribute> options
    ) {
        return new CatalogProductReference(
                key,
                SOURCE,
                null,
                null,
                merchant(merchant),
                product(product),
                variant(variant),
                options
        );
    }

    private ExternalIdentifier merchant(String value) {
        return new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "SHOPIFY", value);
    }

    private ExternalIdentifier product(String value) {
        return new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "SHOPIFY", value);
    }

    private ExternalIdentifier variant(String value) {
        return new ExternalIdentifier(ExternalIdentifierType.VARIANT, "SHOPIFY", value);
    }
}
