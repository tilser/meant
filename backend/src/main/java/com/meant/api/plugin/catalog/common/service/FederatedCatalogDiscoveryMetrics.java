package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailureKind;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FederatedCatalogDiscoveryMetrics {

    private final MeterRegistry meterRegistry;

    void requestCompleted(CatalogDiscoveryTerminalStatus status) {
        Counter.builder("commerce.catalog.federation.requests")
                .tag("outcome", status.name().toLowerCase(Locale.ROOT))
                .register(meterRegistry)
                .increment();
    }

    void sourceFailure(CatalogSourceResult result) {
        CatalogSourceFailureKind kind = result.failure().kind();
        Counter.builder("commerce.catalog.federation.source.failures")
                .tag("provider", result.provider().value().toLowerCase(Locale.ROOT))
                .tag("source", result.discoverySource().value())
                .tag("failure", kind.name().toLowerCase(Locale.ROOT))
                .register(meterRegistry)
                .increment();
    }

    void cancelled() {
        Counter.builder("commerce.catalog.federation.requests")
                .tag("outcome", "cancelled")
                .register(meterRegistry)
                .increment();
    }
}
