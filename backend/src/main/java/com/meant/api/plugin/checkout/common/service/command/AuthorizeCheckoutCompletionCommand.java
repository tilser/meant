package com.meant.api.plugin.checkout.common.service.command;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record AuthorizeCheckoutCompletionCommand(
        @NotBlank String checkoutId,
        UUID cartId
) {
}
