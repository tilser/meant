package com.meant.api.module.cart.service;

import com.meant.api.module.cart.exception.CartException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CartBindingMetrics {
    private final MeterRegistry meterRegistry;

    public void record(CartException.BindingFailure failure) {
        meterRegistry.counter(
                "commerce.cart.offer_binding",
                "outcome",
                failure.name().toLowerCase(Locale.ROOT)
        ).increment();
    }

    public void success() {
        meterRegistry.counter("commerce.cart.offer_binding", "outcome", "success").increment();
    }
}
