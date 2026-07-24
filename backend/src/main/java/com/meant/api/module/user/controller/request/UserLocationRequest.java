package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "A location selected from the validated location suggestions endpoint")
public record UserLocationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "geonames:3067696")
        @NotBlank
        @Pattern(regexp = "(?:geonames:[1-9][0-9]{0,18}|legacy:[A-Za-z]{2}:[^\\r\\n]{1,200})")
        String id
) {
}
