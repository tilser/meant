package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddUserSettingsFilterCommand(
        @NotNull
        UUID id,

        @NotBlank
        String filterId
) {
}
