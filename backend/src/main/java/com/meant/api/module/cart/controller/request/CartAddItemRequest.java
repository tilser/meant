package com.meant.api.module.cart.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CartAddItemRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String productVariantId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Positive
        Integer quantity
) {
}
