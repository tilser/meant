package com.meant.api.module.user.controller.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserProfileRequest(
        @NotBlank
        String firstName,
        String surname
) {
}
