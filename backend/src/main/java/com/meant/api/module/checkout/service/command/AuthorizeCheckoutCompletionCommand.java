package com.meant.api.module.checkout.service.command;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record AuthorizeCheckoutCompletionCommand(
        @NotBlank String checkoutId,
        UUID cartId
) {
}
