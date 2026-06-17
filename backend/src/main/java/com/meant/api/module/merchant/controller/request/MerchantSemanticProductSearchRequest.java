package com.meant.api.module.merchant.controller.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record MerchantSemanticProductSearchRequest(
        @NotBlank
        String query,

        UUID merchantId,

        @Positive
        @Max(1000)
        Integer merchantCandidateLimit,

        @Positive
        @Max(20)
        Integer merchantLimit,

        @Positive
        @Max(50)
        Integer productsPerMerchant,

        @Positive
        @Max(100)
        Integer productLimit
) {
}
