package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.ProductGroupingDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bounded-cardinality reconciliation metrics; tags are controlled enums only. */
@Component
@RequiredArgsConstructor
public class ProductGroupingMetrics {

    private final MeterRegistry meterRegistry;

    static ProductGroupingMetrics noop() {
        return new ProductGroupingMetrics(null);
    }

    void record(List<ProductGroupingDecision> decisions) {
        if (meterRegistry == null) {
            return;
        }
        for (ProductGroupingDecision decision : decisions) {
            Counter.builder("commerce.catalog.grouping.decisions")
                    .tag("outcome", tag(decision.outcome()))
                    .tag("reason", tag(decision.reason()))
                    .register(meterRegistry)
                    .increment();
            decision.contradictions().forEach(contradiction -> Counter
                    .builder("commerce.catalog.grouping.contradictions")
                    .tag("contradiction", tag(contradiction))
                    .register(meterRegistry)
                    .increment());
        }
    }

    private String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
