package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.OfferRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bounded-cardinality ranking metrics. Tags are controlled enums and fixed versions only. */
@Component
@RequiredArgsConstructor
public class ProductRankingMetrics {

    private final MeterRegistry meterRegistry;

    static ProductRankingMetrics noop() {
        return new ProductRankingMetrics(null);
    }

    void recordRequest(Outcome outcome) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("commerce.catalog.ranking.requests")
                .tag("outcome", tag(outcome))
                .tag("product_version", ProductRankingService.PRODUCT_RANKING_VERSION)
                .tag("offer_version", OfferRankingService.OFFER_RANKING_VERSION)
                .register(meterRegistry)
                .increment();
    }

    void recordProductFeatures(List<ProductRankingExplanation.Feature> features) {
        features.forEach(feature -> recordFeature(
                Scope.PRODUCT,
                feature.name().name(),
                feature.availability().name(),
                ProductRankingService.PRODUCT_RANKING_VERSION
        ));
    }

    void recordOfferFeatures(List<OfferRankingExplanation.Feature> features) {
        features.forEach(feature -> recordFeature(
                Scope.OFFER,
                feature.name().name(),
                feature.availability().name(),
                OfferRankingService.OFFER_RANKING_VERSION
        ));
    }

    private void recordFeature(Scope scope, String feature, String availability, String version) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("commerce.catalog.ranking.feature.availability")
                .tag("scope", tag(scope))
                .tag("feature", feature.toLowerCase(Locale.ROOT))
                .tag("availability", availability.toLowerCase(Locale.ROOT))
                .tag("version", version)
                .register(meterRegistry)
                .increment();
    }

    private String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    enum Outcome {
        SUCCESS,
        FALLBACK,
        DEGRADED
    }

    private enum Scope {
        PRODUCT,
        OFFER
    }
}
