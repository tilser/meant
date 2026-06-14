package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantCatalogSearchAttemptResult;
import java.util.UUID;

public record MerchantCatalogSearchAttemptResponse(
        UUID merchantId,
        String domain,
        String name,
        int merchantRank,
        String endpoint,
        int productCount,
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
