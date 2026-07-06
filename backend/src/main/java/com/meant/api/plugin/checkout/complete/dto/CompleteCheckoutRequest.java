package com.meant.api.plugin.checkout.complete.dto;

import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import java.util.List;

public record CompleteCheckoutRequest(
        String checkoutId,
        List<PaymentInstrument> paymentInstruments,
        String checkoutMandate,
        CheckoutSignals signals
) {

    public CompleteCheckoutRequest {
        paymentInstruments = paymentInstruments == null ? List.of() : List.copyOf(paymentInstruments);
    }
}
