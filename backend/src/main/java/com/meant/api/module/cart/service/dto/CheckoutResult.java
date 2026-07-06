package com.meant.api.module.cart.service.dto;

import java.util.List;
import java.util.UUID;

public record CheckoutResult(
        UUID cartId,
        String remoteCartId,
        String checkoutId,
        String status,
        String checkoutUrl,
        String continueUrl,
        String ucpVersion,
        Long totalAmountMinor,
        String currency,
        List<Message> messages,
        boolean nativeCheckoutEnabled
) {

    public CheckoutResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public CheckoutResult(UUID cartId, String remoteCartId, String checkoutUrl, String continueUrl) {
        this(cartId, remoteCartId, null, null, checkoutUrl, continueUrl, null, null, null, List.of(), false);
    }

    public boolean requiresEscalation() {
        return status != null && status.trim().equalsIgnoreCase("requires_escalation");
    }

    public record Message(
            String type,
            String code,
            String severity,
            String content,
            String path
    ) {
    }
}
