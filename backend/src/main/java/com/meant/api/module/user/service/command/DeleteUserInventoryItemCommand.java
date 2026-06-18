package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DeleteUserInventoryItemCommand(
        @NotNull
        UUID userId,

        @NotNull
        UUID itemId
) {
}
