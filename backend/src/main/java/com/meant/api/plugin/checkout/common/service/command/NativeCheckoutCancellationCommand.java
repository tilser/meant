package com.meant.api.plugin.checkout.common.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record NativeCheckoutCancellationCommand(
        @NotNull UUID cartId,
        @NotBlank String checkoutId,
        String reason,
        boolean ap2SecurityLock
) {
}
