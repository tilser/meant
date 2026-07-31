package com.meant.api.module.catalog.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bounded-cardinality counters for offers and products removed by hard eligibility rules. */
@Component
@RequiredArgsConstructor
public class ProductHardEligibilityMetrics {

    private final MeterRegistry meterRegistry;

    static ProductHardEligibilityMetrics noop() {
        return new ProductHardEligibilityMetrics(null);
    }

    void recordCurrencyExcludedOffers(long count) {
        if (meterRegistry == null || count <= 0) {
            return;
        }
        counter("offer").increment(count);
    }

    void recordCurrencyExcludedProduct() {
        if (meterRegistry == null) {
            return;
        }
        counter("product").increment();
    }

    private Counter counter(String scope) {
        return Counter.builder("commerce.catalog.eligibility.currency.exclusions")
                .tag("scope", scope)
                .register(meterRegistry);
    }
}
