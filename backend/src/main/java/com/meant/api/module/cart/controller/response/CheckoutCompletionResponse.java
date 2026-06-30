package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CheckoutCompletionResult;
import com.meant.api.plugin.checkout.common.service.dto.NativeCheckoutStatus;
import java.util.List;
import java.util.UUID;

public record CheckoutCompletionResponse(
        UUID cartId,
        String remoteCartId,
        NativeCheckoutStatus status,
        String checkoutId,
        String orderRef,
        String continueUrl,
        List<String> messages,
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
