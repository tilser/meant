package com.meant.api.module.checkout.service.dto;

import java.time.Instant;
import java.util.UUID;

public record EmbeddedCheckoutSessionBinding(
        UUID sessionId,
        UUID cartId,
        String checkoutId,
        UUID checkoutAttemptId,
        String allowedOrigin,
        String protocolVersion,
        Instant expiresAt
) {
}
