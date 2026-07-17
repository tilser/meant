package com.meant.api.module.cart.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CancelCheckoutCommand(
        @NotNull UUID cartId,
        @NotNull UUID userId,
        @NotBlank String checkoutId,
        String reason,
        boolean ap2SecurityLock,
        String buyerIp
) {
    public CancelCheckoutCommand(
            UUID cartId,
            UUID userId,
            String checkoutId,
            String reason,
            boolean ap2SecurityLock
    ) {
        this(cartId, userId, checkoutId, reason, ap2SecurityLock, null);
    }
}
