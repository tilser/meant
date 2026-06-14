package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import java.util.List;

public record MerchantSemanticProductSearchResponse(
        List<MerchantCatalogSearchAttemptResponse> merchants,
        List<MerchantSemanticProductResponse> products
) {

    public static MerchantSemanticProductSearchResponse from(MerchantSemanticProductSearchResult result) {
        return new MerchantSemanticProductSearchResponse(
                result.merchants().stream()
                        .map(MerchantCatalogSearchAttemptResponse::from)
                        .toList(),
                result.products().stream()
                        .map(MerchantSemanticProductResponse::from)
                        .toList()
        );
    }
}
