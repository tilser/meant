package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateUserProfilePictureCommand(
        @NotNull UUID id,
        @NotBlank @Size(max = 1024) String profilePicturePath
) {
}
