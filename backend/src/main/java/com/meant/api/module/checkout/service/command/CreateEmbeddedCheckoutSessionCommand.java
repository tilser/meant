package com.meant.api.module.checkout.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateEmbeddedCheckoutSessionCommand(
        @NotNull UUID userId,
        @NotNull UUID cartId,
        @NotBlank String checkoutId,
        @NotNull UUID checkoutAttemptId,
        UUID merchantIntegrationId,
        @NotBlank String routingScopeKey,
        @NotBlank String allowedOrigin,
        @NotBlank String protocolVersion
) {
}
