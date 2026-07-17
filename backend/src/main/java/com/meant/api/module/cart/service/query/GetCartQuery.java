package com.meant.api.module.cart.service.query;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GetCartQuery(
        @NotNull
        UUID cartId,
        @NotNull
        UUID userId,
        boolean refresh,
        String buyerIp
) {
    public GetCartQuery(UUID cartId, UUID userId, boolean refresh) {
        this(cartId, userId, refresh, null);
    }
}
