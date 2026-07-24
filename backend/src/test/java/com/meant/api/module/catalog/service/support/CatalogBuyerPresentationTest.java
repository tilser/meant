package com.meant.api.module.catalog.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogBuyerPresentationTest {

    @Test
    void replacesTechnicalAliasesWithVerifiedMerchantOriginAndRejectsEndpointUris() {
        ProviderIdentity provider = new ProviderIdentity("shopify");
        ResultProvenance provenance = new ResultProvenance(
                provider,
                new DiscoverySourceIdentity(
                        provider,
                        ResultSourceType.PROVIDER_CATALOG,
                        "GLOBAL_CATALOG"
                ),
                null,
                new ExternalIdentifier(
                        ExternalIdentifierType.MERCHANT,
                        provider.value(),
                        "gid://shopify/Shop/1"
                ),
                "seller.myshopify.com",
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        provider.value(),
                        "gid://shopify/Product/1"
                ),
                null,
                new ResultFreshness(Instant.parse("2026-07-23T18:30:00Z"), null),
                new ResultSourceReference(
                        ResultSourceType.PROVIDER_CATALOG,
                        "fixture",
                        URI.create("https://seller.myshopify.com/api/ucp/mcp")
                ),
                "nycfactory.com"
        );

        assertThat(CatalogBuyerPresentation.text(
                "Use seller.myshopify.com or /api/mcp",
                List.of(provenance)
        )).isEqualTo("Use nycfactory.com or nycfactory.com");
        assertThat(CatalogBuyerPresentation.safeUri(
                URI.create("https://seller.myshopify.com/api/ucp/mcp/session/1"),
                List.of(provenance)
        )).isNull();
        assertThat(CatalogBuyerPresentation.safeUri(
                URI.create("https://seller.myshopify.com/checkouts/1"),
                List.of(provenance)
        )).isNull();
        assertThat(CatalogBuyerPresentation.safeUri(
                URI.create("https://seller.myshopify.com/custom-transport"),
                List.of(provenance)
        )).isNull();
        assertThat(CatalogBuyerPresentation.safeUri(
                URI.create("https://mcp.gateway.example/custom"),
                List.of(provenance)
        )).isNull();
    }

    @Test
    void mapsEachTechnicalEndpointToItsOwnVerifiedOrigin() {
        ProviderIdentity provider = new ProviderIdentity("shopify");
        ResultProvenance first = provenance(
                provider,
                "nycfactory.com",
                "https://first-store.myshopify.com/api/ucp/mcp",
                "1"
        );
        ResultProvenance second = provenance(
                provider,
                "other-shop.example",
                "https://second-store.myshopify.com/api/ucp/mcp",
                "2"
        );

        assertThat(CatalogBuyerPresentation.text(
                "Compare first-store.myshopify.com with second-store.myshopify.com",
                List.of(first, second)
        )).isEqualTo("Compare nycfactory.com with other-shop.example");
    }

    @Test
    void preservesAProviderVerifiedMyshopifyStorefrontOrigin() {
        ProviderIdentity provider = new ProviderIdentity("shopify");
        ResultProvenance source = provenance(
                provider,
                "official-store.myshopify.com",
                "https://catalog.transport.example/api/ucp/mcp",
                "3"
        );

        assertThat(CatalogBuyerPresentation.text(
                "Shop official-store.myshopify.com",
                List.of(source)
        )).isEqualTo("Shop official-store.myshopify.com");
        assertThat(CatalogBuyerPresentation.safeUri(
                URI.create("https://official-store.myshopify.com/products/shoe"),
                List.of(source)
        )).isEqualTo(URI.create("https://official-store.myshopify.com/products/shoe"));
    }

    @Test
    void preservesASecondVerifiedMyshopifyOriginInAMultiMerchantProduct() {
        ProviderIdentity provider = new ProviderIdentity("shopify");
        ResultProvenance first = provenance(
                provider,
                "first.example",
                "https://first-transport.example/api/ucp/mcp",
                "4"
        );
        ResultProvenance second = provenance(
                provider,
                "official-second.myshopify.com",
                "https://second-transport.example/api/ucp/mcp",
                "5"
        );

        assertThat(CatalogBuyerPresentation.text(
                "Compare first.example with official-second.myshopify.com",
                List.of(first, second)
        )).isEqualTo("Compare first.example with official-second.myshopify.com");
    }

    @Test
    void neverPromotesAnUnverifiedRoutingDomainToBuyerIdentity() {
        ProviderIdentity provider = new ProviderIdentity("shopify");
        ResultProvenance source = new ResultProvenance(
                provider,
                new DiscoverySourceIdentity(
                        provider,
                        ResultSourceType.PROVIDER_CATALOG,
                        "GLOBAL_CATALOG_UNKNOWN"
                ),
                null,
                new ExternalIdentifier(
                        ExternalIdentifierType.MERCHANT,
                        provider.value(),
                        "gid://shopify/Shop/99"
                ),
                "unverified-route.example",
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        provider.value(),
                        "gid://shopify/Product/99"
                ),
                null,
                new ResultFreshness(Instant.parse("2026-07-23T18:30:00Z"), null),
                new ResultSourceReference(
                        ResultSourceType.PROVIDER_CATALOG,
                        "fixture-99",
                        URI.create("https://catalog.shopify.com/api/ucp/mcp")
                )
        );

        assertThat(CatalogBuyerPresentation.merchantOrigin(List.of(source))).isNull();
        assertThat(CatalogBuyerPresentation.text(
                "Continue with unverified-route.example",
                List.of(source)
        )).isEqualTo("Continue with the merchant");
        assertThat(CatalogBuyerPresentation.safeUri(
                URI.create("https://unverified-route.example/products/1"),
                List.of(source)
        )).isNull();
    }

    private ResultProvenance provenance(
            ProviderIdentity provider,
            String merchantOrigin,
            String endpoint,
            String suffix
    ) {
        return new ResultProvenance(
                provider,
                new DiscoverySourceIdentity(
                        provider,
                        ResultSourceType.PROVIDER_CATALOG,
                        "GLOBAL_CATALOG_" + suffix
                ),
                null,
                new ExternalIdentifier(
                        ExternalIdentifierType.MERCHANT,
                        provider.value(),
                        "gid://shopify/Shop/" + suffix
                ),
                URI.create(endpoint).getHost(),
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        provider.value(),
                        "gid://shopify/Product/" + suffix
                ),
                null,
                new ResultFreshness(Instant.parse("2026-07-23T18:30:00Z"), null),
                new ResultSourceReference(
                        ResultSourceType.PROVIDER_CATALOG,
                        "fixture-" + suffix,
                        URI.create(endpoint)
                ),
                merchantOrigin
        );
    }
}
