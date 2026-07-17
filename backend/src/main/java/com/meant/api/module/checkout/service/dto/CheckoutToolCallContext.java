package com.meant.api.module.checkout.service.dto;

import java.util.UUID;

/** Provider-neutral metadata and authentication budget for one logical checkout operation. */
public record CheckoutToolCallContext(
        UUID idempotencyKey,
        boolean unauthorizedRefreshAllowed,
        String buyerIp
) {

    public CheckoutToolCallContext {
        buyerIp = normalizeBuyerIp(buyerIp);
    }

    public CheckoutToolCallContext(UUID idempotencyKey, boolean unauthorizedRefreshAllowed) {
        this(idempotencyKey, unauthorizedRefreshAllowed, null);
    }

    public static CheckoutToolCallContext standard() {
        return new CheckoutToolCallContext(null, true, null);
    }

    public static CheckoutToolCallContext forBuyer(String buyerIp) {
        return new CheckoutToolCallContext(null, true, buyerIp);
    }

    public CheckoutToolCallContext reconciliation() {
        return new CheckoutToolCallContext(idempotencyKey, false, buyerIp);
    }

    private static String normalizeBuyerIp(String buyerIp) {
        if (buyerIp == null || buyerIp.isBlank()) {
            return null;
        }
        String normalized = buyerIp.trim();
        if (normalized.length() > 128 || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Buyer IP is invalid");
        }
        return normalized;
    }
}
