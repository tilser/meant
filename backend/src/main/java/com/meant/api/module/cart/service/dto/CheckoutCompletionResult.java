package com.meant.api.module.cart.service.dto;

import com.meant.api.plugin.checkout.common.service.dto.NativeCheckoutStatus;
import java.util.List;
import java.util.UUID;

public record CheckoutCompletionResult(
        UUID cartId,
        String remoteCartId,
        NativeCheckoutStatus status,
        String checkoutId,
        String orderRef,
        String continueUrl,
        List<String> messages,
        boolean nativeAttempted
) {

    public CheckoutCompletionResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
