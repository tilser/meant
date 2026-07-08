package com.meant.api.module.cart.service.dto;

public record CheckoutAssistResult(
        String reply,
        boolean checkoutUpdated,
        CheckoutResult checkout
) {
}
