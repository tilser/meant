package com.meant.api.module.user.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserProfilePictureRequest(
        @NotBlank
        @Size(max = 1024)
        String profilePicturePath
) {
}
