package com.meant.api.module.merchant.service.dto;

import java.util.UUID;

public record MerchantCatalogSearchAttemptResult(
        UUID merchantId,
        String domain,
        String name,
        int merchantRank,
        String endpoint,
        int productCount,
        String error
) {

    public static MerchantCatalogSearchAttemptResult success(
            MerchantSemanticSearchResult merchant,
            String endpoint,
            int productCount
    ) {
        return new MerchantCatalogSearchAttemptResult(
                merchant.merchantId(),
                merchant.domain(),
                merchant.name(),
                merchant.rank(),
                endpoint,
                productCount,
                null
        );
    }

    public static MerchantCatalogSearchAttemptResult failure(
            MerchantSemanticSearchResult merchant,
            String error
    ) {
        return new MerchantCatalogSearchAttemptResult(
                merchant.merchantId(),
                merchant.domain(),
                merchant.name(),
                merchant.rank(),
                null,
                0,
                error
        );
    }
}
