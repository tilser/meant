package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchPriceFilter;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogSearchRequest;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShopifyGlobalCatalogDiscoverySourceTest {

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
                null,
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
    void providerWideSourceDoesNotRunForExplicitMerchantScope() {
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                new FakeProvider(properties(), null, null),
                properties(),
                authProperties(true)
        );

        assertThat(adapter.supports(new CatalogDiscoveryRequest(
                "linen shirt",
                java.util.UUID.randomUUID(),
                10,
                null,
                null,
                null
        ))).isFalse();
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
    void discoveryEnabledAuthDisabledDoesNotScheduleGlobalCatalog() {
        ShopifyGlobalCatalogDiscoverySource adapter = new ShopifyGlobalCatalogDiscoverySource(
                new FakeProvider(properties(true), null, null),
                properties(true),
                authProperties(false)
        );

        assertThat(adapter.supports(broadRequest())).isFalse();
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

    private ShopifyGlobalCatalogProperties properties() {
        return properties(true);
    }

    private ShopifyGlobalCatalogProperties properties(boolean discoveryEnabled) {
        return new ShopifyGlobalCatalogProperties(
                discoveryEnabled,
                java.net.URI.create("https://catalog.shopify.test/api/ucp/mcp"),
                Set.of("catalog.shopify.test"),
                "2026-04-08",
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

    private CatalogDiscoveryRequest broadRequest() {
        return new CatalogDiscoveryRequest("linen shirt", null, 10, null, null, null);
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
        private final CatalogSourceResult result;
        private ShopifyGlobalCatalogSearchRequest request;
        private int calls;

        private FakeProvider(
                ShopifyGlobalCatalogProperties properties,
                DiscoverySourceIdentity source,
                CatalogSourceResult result
        ) {
            super(null, null, null, null, properties);
            this.source = source;
            this.result = result;
        }

        @Override
        public DiscoverySourceIdentity discoverySourceIdentity() {
            return source;
        }

        @Override
        public CatalogSourceResult searchCatalog(ShopifyGlobalCatalogSearchRequest request) {
            this.request = request;
            calls++;
            return result;
        }
    }
}
