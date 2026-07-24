package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
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
        int productCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String error
) {

    public static MerchantCatalogSearchAttemptResponse from(MerchantCatalogSearchAttemptResult result) {
        String domain = MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(result.domain());
        return new MerchantCatalogSearchAttemptResponse(
                result.merchantId(),
                domain == null ? "Merchant" : domain,
                MerchantBuyerTextSanitizer.sanitize(
                        result.name(),
                        result.domain(),
                        null,
                        result.endpoint()
                ),
                result.merchantRank(),
                result.productCount(),
                MerchantBuyerTextSanitizer.sanitize(
                        result.error(),
                        result.domain(),
                        null,
                        result.endpoint()
                )
        );
    }
}
