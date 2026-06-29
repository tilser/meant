package com.meant.api.plugin.checkout.common.service.command;

import jakarta.validation.constraints.NotBlank;

public record StartCheckoutCompletionCommand(
        @NotBlank String checkoutId
) {
}
