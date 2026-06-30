package com.meant.api.plugin.checkout.cancel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CancelCheckoutArguments(
        @JsonProperty("checkout_id")
        String checkoutId,
        String reason
) {
}
