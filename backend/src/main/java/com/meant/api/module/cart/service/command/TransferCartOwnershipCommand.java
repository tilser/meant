package com.meant.api.module.cart.service.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record TransferCartOwnershipCommand(
        @NotNull UUID sourceUserId,
        @NotNull UUID targetUserId
) {
}
