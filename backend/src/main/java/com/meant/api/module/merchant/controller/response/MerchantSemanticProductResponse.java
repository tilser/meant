package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import java.util.UUID;

public record MerchantSemanticProductResponse(
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

    public static MerchantSemanticProductResponse from(MerchantSemanticProductResult result) {
        return new MerchantSemanticProductResponse(
                result.merchantId(),
                result.merchantDomain(),
                result.merchantName(),
                result.endpoint(),
                result.merchantRank(),
                result.merchantSemanticScore(),
                result.merchantRerankScore(),
                result.productId(),
                result.title(),
                result.descriptionHtml(),
                result.url(),
                result.imageUrl(),
                result.priceMinAmount(),
                result.priceMaxAmount(),
                result.priceCurrency(),
                result.available(),
                result.catalogRank(),
                result.productRerankScore(),
                result.rank()
        );
    }
}
