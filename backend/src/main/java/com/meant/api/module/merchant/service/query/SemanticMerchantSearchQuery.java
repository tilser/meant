package com.meant.api.module.merchant.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SemanticMerchantSearchQuery(
        @NotBlank
        String query,

        @Positive
        int limit
) {
}
