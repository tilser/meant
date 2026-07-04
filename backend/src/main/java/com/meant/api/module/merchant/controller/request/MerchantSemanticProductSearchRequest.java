package com.meant.api.module.merchant.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record MerchantSemanticProductSearchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String query,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Positive
        @Max(1000)
        Integer merchantCandidateLimit,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Positive
        @Max(20)
        Integer merchantLimit,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Positive
        @Max(50)
        Integer productsPerMerchant,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Positive
        @Max(100)
        Integer productLimit
) {
}
