package com.meant.api.module.checkout.service.dto;

import java.time.Instant;
import java.util.UUID;

public record EmbeddedCheckoutSessionBinding(
        UUID sessionId,
        UUID cartId,
        String checkoutId,
        String allowedOrigin,
        String protocolVersion,
        Instant expiresAt
) {
}
