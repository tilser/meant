package com.meant.api.module.merchant.service.dto;

import java.util.UUID;

public record MerchantSemanticProductResult(
        UUID merchantId,
        String merchantDomain,
        String merchantName,
        String endpoint,
        int merchantRank,
        double merchantSemanticScore,
        double merchantRerankScore,
        String productId,
        String title,
        String descriptionHtml,
        String url,
        String imageUrl,
        Long priceMinAmount,
        Long priceMaxAmount,
        String priceCurrency,
        Boolean available,
        int catalogRank,
        double productRerankScore,
        int rank
) {
}
