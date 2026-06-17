package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProductDetailsArguments(
        @JsonProperty("product_id")
        String productId,

        Map<String, String> options,

        String country,

        String language
) {

    public ProductDetailsArguments(String productId) {
        this(productId, null, null, null);
    }
}
