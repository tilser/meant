package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CartAddItem(
        @JsonProperty("product_variant_id")
        String productVariantId,
        Integer quantity
) {
}
