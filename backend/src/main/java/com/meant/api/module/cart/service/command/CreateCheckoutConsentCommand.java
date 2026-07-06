package com.meant.api.module.cart.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCheckoutConsentCommand(
        @NotNull UUID cartId,
        @NotNull UUID userId,
        @NotBlank String checkoutId,
        @NotBlank String paymentInstrumentReference,
        String shippingMethod,
        String presentedTermsHash
) {
}
