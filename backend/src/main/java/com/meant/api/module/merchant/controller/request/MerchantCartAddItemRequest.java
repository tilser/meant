package com.meant.api.module.merchant.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record MerchantCartAddItemRequest(
        @NotBlank
        String productVariantId,
        @Positive
        Integer quantity
) {
}
