package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CheckoutConsentResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Stored buyer consent artifact for a checkout session.")
public record CheckoutConsentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID buyerConsentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt
) {

    public static CheckoutConsentResponse from(CheckoutConsentResult result) {
        return new CheckoutConsentResponse(result.buyerConsentId(), result.expiresAt());
    }
}
