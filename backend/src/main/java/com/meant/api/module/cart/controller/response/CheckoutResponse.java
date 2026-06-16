package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CheckoutResult;
import java.util.UUID;

public record CheckoutResponse(
        UUID cartId,
        String remoteCartId,
        String checkoutUrl
) {

    public static CheckoutResponse from(CheckoutResult result) {
        return new CheckoutResponse(result.cartId(), result.remoteCartId(), result.checkoutUrl());
    }
}
