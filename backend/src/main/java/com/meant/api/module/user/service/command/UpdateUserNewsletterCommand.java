package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateUserNewsletterCommand(
        @NotNull UUID id,
        @NotNull Boolean newsletter
) {
}
