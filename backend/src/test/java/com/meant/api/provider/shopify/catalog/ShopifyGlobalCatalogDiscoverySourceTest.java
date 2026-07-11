package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryRequest;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchPriceFilter;
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogSearchRequest;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import java.time.Duration;
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
                new CatalogSearchFilters(List.of("apparel"), new CatalogSearchPriceFilter(1000L, 5000L))
        ), ignored -> { });

        assertThat(result.successful()).isTrue();
        assertThat(provider.calls).isEqualTo(1);
        assertThat(provider.request.query()).isEqualTo("linen shirt");
        assertThat(provider.request.limit()).isEqualTo(12);
        assertThat(provider.request.context().addressCountry()).isEqualTo("US");
        assertThat(provider.request.filters().price().min()).isEqualTo(1000L);
        assertThat(provider.request.filters().categories()).extracting(category -> category.id())
                .containsExactly("apparel");
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
