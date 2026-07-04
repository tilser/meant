package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UserLocationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String country,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String city
) {
}
