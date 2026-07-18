package com.meant.api.module.cart.service.dto;

import com.meant.api.module.cart.constant.EmbeddedCheckoutBootstrapAction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EmbeddedCheckoutBootstrapResult(
        EmbeddedCheckoutBootstrapAction action,
        UUID sessionId,
        UUID cartId,
        String checkoutId,
        UUID checkoutAttemptId,
        String checkoutUrl,
        String fallbackContinueUrl,
        String protocolVersion,
        String ecAuth,
        List<String> allowedDelegations,
        Instant expiresAt,
        String merchantProvider,
        String merchantDomain,
        String reason
) {
    public EmbeddedCheckoutBootstrapResult {
        allowedDelegations = allowedDelegations == null ? List.of() : List.copyOf(allowedDelegations);
    }
}
