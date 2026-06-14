package com.meant.api.module.merchant.service.dto;

import java.util.UUID;

public record MerchantSemanticSearchResult(
        UUID merchantId,
        String domain,
        String name,
        String retrievalContent,
        double semanticScore,
        double rerankScore,
        int rank
) {
}
