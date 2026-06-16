package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateUserProfileCommand(
        @NotNull UUID id,
        String firstName,
        String surname
) {
}
