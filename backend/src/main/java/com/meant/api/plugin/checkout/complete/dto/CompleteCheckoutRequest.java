package com.meant.api.plugin.checkout.complete.dto;

import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CompleteCheckoutRequest(
        String checkoutId,
        List<PaymentInstrument> paymentInstruments,
        String checkoutMandate,
        Map<String, Object> signals
) {

    public CompleteCheckoutRequest {
        paymentInstruments = paymentInstruments == null ? List.of() : List.copyOf(paymentInstruments);
        signals = signals == null ? Map.of() : new LinkedHashMap<>(signals);
    }
}
