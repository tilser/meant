package com.meant.api.plugin.catalog.shopify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationContext;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationStatus;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceResult;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifier;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailability;
import com.meant.api.plugin.catalog.common.dto.OfferIdentity;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogLookupRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShopifyCatalogProductRehydrationProviderTest {
    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");

    @Test
    void rehydratesSelectedShopifyProductThroughExistingLookupProvider() {
        ShopifyGlobalCatalogProvider globalProvider = mock(ShopifyGlobalCatalogProvider.class);
        ShopifyGlobalCatalogProperties catalogProperties = mock(ShopifyGlobalCatalogProperties.class);
        when(catalogProperties.maximumLookupIds()).thenReturn(50);
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("SHOPIFY"),
                ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL_CATALOG"
        );
        when(globalProvider.discoverySourceIdentity()).thenReturn(source);
        CatalogSourceResult sourceResult = mock(CatalogSourceResult.class);
        when(sourceResult.successful()).thenReturn(true);
        ProductCandidate candidate = candidate(source);
        when(sourceResult.candidates()).thenReturn(List.of(candidate));
        when(globalProvider.lookupCatalog(any())).thenReturn(sourceResult);
        ShopifyCatalogProductRehydrationProvider provider = new ShopifyCatalogProductRehydrationProvider(
                globalProvider,
                catalogProperties,
                new ShopifyCatalogDataUseProperties(
                        false,
                        Duration.ofMinutes(15),
                        Duration.ofMinutes(2),
                        Duration.ofDays(30)
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        CatalogProductReference reference = new CatalogProductReference(
                "saved-1",
                source,
                null,
                null,
                new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "SHOPIFY", "merchant-1"),
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "SHOPIFY", "product-1"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "SHOPIFY", "variant-1"),
                List.of()
        );

        var result = provider.rehydrate(List.of(reference), new CatalogRehydrationContext("CZ", "en")).getFirst();

        assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.facts().freshness().freshUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        verify(globalProvider).lookupCatalog(any(ShopifyGlobalCatalogLookupRequest.class));
    }

    private ProductCandidate candidate(DiscoverySourceIdentity source) {
        ProductCandidate candidate = mock(ProductCandidate.class);
        ResultProvenance provenance = mock(ResultProvenance.class);
        when(provenance.externalProductReference()).thenReturn(
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "SHOPIFY", "product-1"));
        when(provenance.externalVariantReference()).thenReturn(
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "SHOPIFY", "variant-1"));
        when(candidate.provenance()).thenReturn(List.of(provenance));
        when(candidate.title()).thenReturn("Current Shopify product");
        when(candidate.media()).thenReturn(List.of());
        Offer offer = mock(Offer.class);
        OfferIdentity identity = mock(OfferIdentity.class);
        when(identity.externalVariantIdentity()).thenReturn(
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "SHOPIFY", "variant-1"));
        when(offer.identity()).thenReturn(identity);
        when(offer.availability()).thenReturn(OfferAvailability.unknown());
        when(offer.selectedOptions()).thenReturn(List.of());
        when(offer.delivery()).thenReturn(List.of());
        when(candidate.offer()).thenReturn(offer);
        return candidate;
    }
}
