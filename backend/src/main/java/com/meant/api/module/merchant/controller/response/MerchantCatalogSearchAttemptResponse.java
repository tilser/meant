package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchAttemptResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record MerchantCatalogSearchAttemptResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String domain,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int merchantRank,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String endpoint,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int productCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String error
) {

    public static MerchantCatalogSearchAttemptResponse from(MerchantCatalogSearchAttemptResult result) {
        return new MerchantCatalogSearchAttemptResponse(
                result.merchantId(),
                result.domain(),
                result.name(),
                result.merchantRank(),
                result.endpoint(),
                result.productCount(),
                result.error()
        );
    }
}
