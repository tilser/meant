package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.entity.UserProductSearchResultItem;
import java.util.List;
import java.util.UUID;

public record UserProductSearchProductResult(
        String productKey,
        String productHash,
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
        String detailError,
        String detailDescription,
        String detailImageUrl,
        String detailPriceMin,
        String detailPriceMax,
        String detailPriceCurrency,
        String selectedVariantId,
        String selectedVariantTitle,
        String selectedVariantPriceAmount,
        String selectedVariantPriceCurrency,
        String selectedVariantImageUrl,
        String selectedVariantImageAltText,
        Boolean selectedVariantAvailable,
        int catalogRank,
        double productRerankScore,
        int rank,
        int matchScore,
        String whyMeantForYou,
        List<String> matchedFilterIds,
        List<String> missedFilterIds
) {

    public static UserProductSearchProductResult from(
            UserProductSearchResultItem item,
            UserProductRecommendationExplanationResult explanation
    ) {
        return new UserProductSearchProductResult(
                item.getProductKey(),
                item.getProductHash(),
                item.getMerchantId(),
                item.getMerchantDomain(),
                item.getMerchantName(),
                item.getEndpoint(),
                item.getMerchantRank(),
                item.getMerchantSemanticScore(),
                item.getMerchantRerankScore(),
                item.getProductId(),
                item.getTitle(),
                item.getDescriptionHtml(),
                item.getUrl(),
                item.getImageUrl(),
                item.getPriceMinAmount(),
                item.getPriceMaxAmount(),
                item.getPriceCurrency(),
                item.getAvailable(),
                item.getDetailError(),
                item.getDetailDescription(),
                item.getDetailImageUrl(),
                item.getDetailPriceMin(),
                item.getDetailPriceMax(),
                item.getDetailPriceCurrency(),
                item.getSelectedVariantId(),
                item.getSelectedVariantTitle(),
                item.getSelectedVariantPriceAmount(),
                item.getSelectedVariantPriceCurrency(),
                item.getSelectedVariantImageUrl(),
                item.getSelectedVariantImageAltText(),
                item.getSelectedVariantAvailable(),
                item.getCatalogRank(),
                item.getProductRerankScore(),
                item.getRank(),
                matchScore(item, explanation),
                explanation.whyMeantForYou(),
                explanation.matchedFilterIds(),
                explanation.missedFilterIds()
        );
    }

    private static int matchScore(
            UserProductSearchResultItem item,
            UserProductRecommendationExplanationResult explanation
    ) {
        int base = (int) Math.round(68 + item.getProductRerankScore() * 22);
        int preferenceBoost = explanation.matchedFilterIds().size() * 3;
        int missPenalty = explanation.missedFilterIds().size() * 8;
        int rankPenalty = Math.max(0, item.getRank() - 1);
        return Math.max(35, Math.min(99, base + preferenceBoost - missPenalty - rankPenalty));
    }
}
