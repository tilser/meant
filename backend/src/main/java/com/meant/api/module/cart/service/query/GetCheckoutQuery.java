package com.meant.api.module.cart.service.query;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GetCheckoutQuery(
        @NotNull
        UUID cartId,
        @NotNull
        UUID userId,
        boolean refresh
) {
}
