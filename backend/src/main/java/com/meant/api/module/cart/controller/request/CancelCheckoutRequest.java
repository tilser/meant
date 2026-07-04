package com.meant.api.module.cart.controller.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CancelCheckoutRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("checkout_id")
        @NotBlank
        String checkoutId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String reason,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @JsonProperty("ap2_security_lock")
        boolean ap2SecurityLock
) {
}
