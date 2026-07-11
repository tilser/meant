package com.meant.api.module.checkout.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UseEmbeddedCheckoutSessionCommand(
        @NotNull UUID sessionId,
        @NotNull UUID userId,
        @NotNull UUID cartId,
        @NotBlank String checkoutId,
        UUID merchantIntegrationId,
        @NotBlank String routingScopeKey,
        @NotBlank String allowedOrigin
) {
}
