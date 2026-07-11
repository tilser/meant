package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared source-level instrumentation for adapters that do not already own equivalent metrics. */
@Component
@RequiredArgsConstructor
public class CatalogDiscoverySourceMetrics {

    private final MeterRegistry meterRegistry;

    public void record(CatalogSourceResult result, long elapsedNanos) {
        String provider = result.provider().value().toLowerCase(Locale.ROOT);
        String outcome = result.successful()
                ? "success"
                : result.failure().kind().name().toLowerCase(Locale.ROOT);
        Counter.builder("commerce.catalog.source.calls")
                .tag("provider", provider)
                .tag("source", result.discoverySource().value())
                .tag("operation", "search")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
        Timer.builder("commerce.catalog.source.duration")
                .tag("provider", provider)
                .tag("source", result.discoverySource().value())
                .tag("operation", "search")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(Duration.ofNanos(Math.max(0, elapsedNanos)));
        if (result.successful()) {
            DistributionSummary.builder("commerce.catalog.source.candidates")
                    .tag("provider", provider)
                    .tag("source", result.discoverySource().value())
                    .tag("operation", "search")
                    .register(meterRegistry)
                    .record(result.candidates().size());
        }
    }
}
