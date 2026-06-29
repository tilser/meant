package com.meant.api.module.cart.service.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CancelCartCommand(
        @NotNull
        UUID cartId,
        @NotNull
        UUID userId
) {
}
