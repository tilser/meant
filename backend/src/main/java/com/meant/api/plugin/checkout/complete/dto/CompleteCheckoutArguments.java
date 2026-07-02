package com.meant.api.plugin.checkout.complete.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.checkout.extension.ap2mandate.dto.Ap2CheckoutData;
import com.meant.api.plugin.payment.common.dto.PaymentInstrument;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompleteCheckoutArguments(
        @JsonProperty("checkout_id")
        String checkoutId,
        Payment payment,
        Ap2CheckoutData ap2,
        Map<String, Object> signals
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Payment(
            List<PaymentInstrument> instruments
    ) {
    }
}
