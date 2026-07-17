package com.meant.api.module.cart.service.dto;

import java.util.UUID;

/** Provider-neutral transport metadata controlled by the server. */
public record CartToolCallContext(UUID idempotencyKey, String buyerIp) {

    public CartToolCallContext {
        buyerIp = normalizeBuyerIp(buyerIp);
    }

    public CartToolCallContext(UUID idempotencyKey) {
        this(idempotencyKey, null);
    }

    public static CartToolCallContext standard() {
        return new CartToolCallContext(null, null);
    }

    public static CartToolCallContext forBuyer(String buyerIp) {
        return new CartToolCallContext(null, buyerIp);
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
