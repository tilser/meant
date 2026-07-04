package com.meant.api.module.merchant.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record MerchantSemanticSearchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String query,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
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
