package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Newsletter subscription update for the authenticated user.")
public record UpdateUserNewsletterRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Boolean newsletter
) {
}
