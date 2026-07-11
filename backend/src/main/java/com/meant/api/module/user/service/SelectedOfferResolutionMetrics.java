package com.meant.api.module.user.service;

import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SelectedOfferResolutionMetrics {
    private final MeterRegistry meterRegistry;

    void record(SelectedOfferResolutionException.Failure failure) {
        meterRegistry.counter("cart.offer.resolution", "outcome", failure.name().toLowerCase()).increment();
    }

    void recordSuccess() {
        meterRegistry.counter("cart.offer.resolution", "outcome", "success").increment();
    }
}
