package com.meant.api.module.merchant.service.dto;

public record ProductDetailsResult(
        String endpoint,
        String rawResponse,
        ProductDetailsResponse.Product product
) {
}
