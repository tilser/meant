package com.meant.api.module.merchant.service.dto;

import java.util.List;

public record MerchantSemanticProductSearchResult(
        List<MerchantCatalogSearchAttemptResult> merchants,
        List<MerchantSemanticProductResult> products,
        boolean truncated
) {

    public MerchantSemanticProductSearchResult(
            List<MerchantCatalogSearchAttemptResult> merchants,
            List<MerchantSemanticProductResult> products
    ) {
        this(merchants, products, false);
    }
}
