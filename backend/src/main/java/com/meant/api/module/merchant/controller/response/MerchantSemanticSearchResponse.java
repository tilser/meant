package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.util.UUID;

public record MerchantSemanticSearchResponse(
        UUID merchantId,
        String domain,
        String name,
        String retrievalContent,
        double semanticScore,
        double rerankScore,
        int rank
) {

    public static MerchantSemanticSearchResponse from(MerchantSemanticSearchResult result) {
        return new MerchantSemanticSearchResponse(
                result.merchantId(),
                result.domain(),
                result.name(),
                result.retrievalContent(),
                result.semanticScore(),
                result.rerankScore(),
                result.rank()
        );
    }
}
