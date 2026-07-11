package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bounded-cardinality policy instrumentation. No provider payload or caller identity becomes a tag. */
@Component
@RequiredArgsConstructor
public class CatalogDataUsePolicyMetrics {
    private final MeterRegistry meterRegistry;

    public void record(CatalogPayloadClass payloadClass, CatalogRetentionDecision decision, String resolution) {
        Counter.builder("commerce.catalog.data_use.decisions")
                .tag("payload_class", payloadClass.name().toLowerCase(java.util.Locale.ROOT))
                .tag("mode", decision.mode().name().toLowerCase(java.util.Locale.ROOT))
                .tag("resolution", resolution)
                .register(meterRegistry)
                .increment();
    }
}
