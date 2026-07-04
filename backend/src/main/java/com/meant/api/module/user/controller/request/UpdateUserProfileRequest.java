package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserProfileRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String firstName,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String surname
) {
}
