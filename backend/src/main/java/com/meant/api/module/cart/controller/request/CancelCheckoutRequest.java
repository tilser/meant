package com.meant.api.module.cart.controller.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CancelCheckoutRequest(
        @JsonProperty("checkout_id")
        @NotBlank String checkoutId,
        String reason,
        @JsonProperty("ap2_security_lock")
        boolean ap2SecurityLock
) {
}
