package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CheckoutCompletionResult;
import com.meant.api.module.checkout.service.dto.NativeCheckoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record CheckoutCompletionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID cartId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String remoteCartId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        NativeCheckoutStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String checkoutId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String orderRef,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String continueUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> messages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean nativeAttempted
) {

    public static CheckoutCompletionResponse from(CheckoutCompletionResult result) {
        return new CheckoutCompletionResponse(
                result.cartId(),
                result.remoteCartId(),
                result.status(),
                result.checkoutId(),
                result.orderRef(),
                result.continueUrl(),
                result.messages(),
                result.nativeAttempted()
        );
    }
}
