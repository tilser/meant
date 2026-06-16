package com.meant.api.module.cart.service.dto;

import java.util.UUID;

public record CheckoutResult(
        UUID cartId,
        String remoteCartId,
        String checkoutUrl
) {
}
