package com.meant.api.plugin.checkout.extension.ap2mandate.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Ap2CheckoutData(
        @JsonProperty("merchant_authorization")
        String merchantAuthorization,
        @JsonProperty("checkout_mandate")
        String checkoutMandate
) {
}
