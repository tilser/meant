package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductDetailsArguments(
        @JsonProperty("product_id")
        String productId
) {
}
