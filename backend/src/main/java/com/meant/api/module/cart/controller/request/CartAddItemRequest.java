package com.meant.api.module.cart.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CartAddItemRequest(
        @NotBlank
        String productVariantId,
        @Positive
        Integer quantity
) {
}
