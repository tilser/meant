package com.meant.api.plugin.checkout.cancel.dto;

public record CancelCheckoutRequest(
        String checkoutId,
        String reason
) {
}
