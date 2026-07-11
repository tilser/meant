package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CatalogProductRehydrationResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogProductRehydrationMetrics {
    private final MeterRegistry meterRegistry;

    public void record(CatalogProductRehydrationResult result) {
        Counter.builder("commerce.catalog.rehydration.results")
                .tag("status", result.status().name().toLowerCase(java.util.Locale.ROOT))
                .tag("failure", result.failure() == null
                        ? "none"
                        : result.failure().name().toLowerCase(java.util.Locale.ROOT))
                .register(meterRegistry)
                .increment();
    }
}
