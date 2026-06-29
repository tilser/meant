package com.meant.api.plugin.checkout.update.dto;

import java.util.Map;

public record UpdateCheckoutRequest(
        String checkoutId,
        Map<String, Object> buyer,
        String email,
        Map<String, Object> shippingAddress
) {
}
