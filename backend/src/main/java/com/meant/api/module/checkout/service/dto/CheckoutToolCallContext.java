package com.meant.api.module.checkout.service.dto;

import java.util.UUID;

/** Provider-neutral metadata and authentication budget for one logical checkout operation. */
public record CheckoutToolCallContext(UUID idempotencyKey, boolean unauthorizedRefreshAllowed) {
    public static CheckoutToolCallContext standard() {
        return new CheckoutToolCallContext(null, true);
    }

    public CheckoutToolCallContext reconciliation() {
        return new CheckoutToolCallContext(idempotencyKey, false);
    }
}
