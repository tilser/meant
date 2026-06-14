package com.meant.api.module.merchant.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record MerchantSemanticSearchRequest(
        @NotBlank
        String query,

        @Positive
        @Max(1000)
        Integer limit
) {

    private static final int DEFAULT_LIMIT = 10;

    public int resolvedLimit() {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return limit;
    }
}
