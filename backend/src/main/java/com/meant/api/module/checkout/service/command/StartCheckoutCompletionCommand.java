package com.meant.api.module.checkout.service.command;

import jakarta.validation.constraints.NotBlank;

public record StartCheckoutCompletionCommand(
        @NotBlank String checkoutId
) {
}
