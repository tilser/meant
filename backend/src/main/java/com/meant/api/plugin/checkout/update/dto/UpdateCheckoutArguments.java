package com.meant.api.plugin.checkout.update.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateCheckoutArguments(
        @JsonProperty("checkout_id")
        String checkoutId,
        Map<String, Object> buyer,
        String email,
        Fulfillment fulfillment
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Fulfillment(
            @JsonProperty("shipping_address")
            Map<String, Object> shippingAddress
    ) {
    }
}
