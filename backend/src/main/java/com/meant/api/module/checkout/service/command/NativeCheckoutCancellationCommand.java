package com.meant.api.module.checkout.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record NativeCheckoutCancellationCommand(
        @NotNull UUID cartId,
        @NotBlank String checkoutId,
        String reason,
        boolean ap2SecurityLock,
        String buyerIp
) {
    public NativeCheckoutCancellationCommand(
            UUID cartId,
            String checkoutId,
            String reason,
            boolean ap2SecurityLock
    ) {
        this(cartId, checkoutId, reason, ap2SecurityLock, null);
    }
}
