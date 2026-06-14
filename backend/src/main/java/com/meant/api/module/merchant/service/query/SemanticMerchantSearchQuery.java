package com.meant.api.module.merchant.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

public record SemanticMerchantSearchQuery(
        @NotBlank
        String query,

        @Positive
        @Max(1000)
        int limit
) {
}
