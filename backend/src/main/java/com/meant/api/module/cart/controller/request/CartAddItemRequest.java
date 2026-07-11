package com.meant.api.module.cart.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CartAddItemRequest(
        @NotBlank
        @Schema(description = "Server-issued exact offer key from the authenticated user's live product session",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String offerKey,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Positive
        Integer quantity
) {
}
