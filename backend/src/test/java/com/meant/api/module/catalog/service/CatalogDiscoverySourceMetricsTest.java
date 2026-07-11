package com.meant.api.module.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogDiscoverySourceMetricsTest {

    @Test
    void recordsBoundedProviderLatencyAndCandidateMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProviderIdentity provider = new ProviderIdentity("GENERIC_UCP");
        CatalogSourceResult result = new CatalogSourceResult(
                provider,
                new DiscoverySourceIdentity(provider, ResultSourceType.MERCHANT_STOREFRONT, "MEANT_MERCHANT_SEMANTIC"),
                CatalogSourceOperation.SEARCH,
                null,
                NegotiatedCapabilities.none(),
                List.of(),
                null,
                false,
                null
        );

        new CatalogDiscoverySourceMetrics(registry).record(result, 1_000_000L);

        assertThat(registry.get("commerce.catalog.source.calls")
                .tag("provider", "generic_ucp")
                .tag("source", "MEANT_MERCHANT_SEMANTIC")
                .tag("operation", "search")
                .tag("outcome", "success")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("commerce.catalog.source.duration")
                .tag("provider", "generic_ucp")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.get("commerce.catalog.source.candidates")
                .summary().count()).isEqualTo(1L);
    }
}
