package com.meant.api.module.cart.service.dto;

import java.util.UUID;

/** Provider-neutral transport metadata controlled by the server. */
public record CartToolCallContext(UUID idempotencyKey) {
    public static CartToolCallContext standard() {
        return new CartToolCallContext(null);
    }
}
