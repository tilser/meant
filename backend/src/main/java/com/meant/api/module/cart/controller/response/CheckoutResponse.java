package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CheckoutResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record CheckoutResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID cartId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String remoteCartId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String checkoutUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String continueUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean nativeCheckoutEnabled
) {

    public static CheckoutResponse from(CheckoutResult result) {
        return new CheckoutResponse(
                result.cartId(),
                result.remoteCartId(),
                result.checkoutUrl(),
                result.continueUrl(),
                result.nativeCheckoutEnabled()
        );
    }
}
