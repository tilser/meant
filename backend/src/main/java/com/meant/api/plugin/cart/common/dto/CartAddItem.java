package com.meant.api.plugin.cart.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CartAddItem(
        @JsonProperty("product_variant_id")
        String productVariantId,
        Integer quantity
) {
}
