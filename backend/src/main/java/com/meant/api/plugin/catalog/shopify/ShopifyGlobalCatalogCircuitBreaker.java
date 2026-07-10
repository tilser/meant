package com.meant.api.plugin.catalog.shopify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Small count-based breaker with a single half-open probe for the shared Shopify upstream. */
@Component
public class ShopifyGlobalCatalogCircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private int consecutiveFailures;
    private Instant openUntil;
    private boolean halfOpenProbeInFlight;

    @Autowired
    public ShopifyGlobalCatalogCircuitBreaker(ShopifyGlobalCatalogProperties properties) {
        this(properties.circuitFailureThreshold(), properties.circuitOpenDuration(), Clock.systemUTC());
    }

    ShopifyGlobalCatalogCircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public synchronized boolean tryAcquire() {
        if (openUntil == null) {
            return true;
        }
        if (clock.instant().isBefore(openUntil) || halfOpenProbeInFlight) {
            return false;
        }
        halfOpenProbeInFlight = true;
        return true;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openUntil = null;
        halfOpenProbeInFlight = false;
    }

    public synchronized void recordFailure(Duration minimumOpenDuration) {
        halfOpenProbeInFlight = false;
        consecutiveFailures++;
        if (openUntil != null || consecutiveFailures >= failureThreshold) {
            Duration duration = longer(openDuration, minimumOpenDuration);
            openUntil = clock.instant().plus(duration);
        }
    }

    public synchronized void recordIgnoredFailure() {
        if (halfOpenProbeInFlight) {
            recordSuccess();
        }
    }

    public synchronized boolean isOpen() {
        return openUntil != null && (clock.instant().isBefore(openUntil) || halfOpenProbeInFlight);
    }

    private Duration longer(Duration first, Duration second) {
        return second != null && second.compareTo(first) > 0 ? second : first;
    }
}
