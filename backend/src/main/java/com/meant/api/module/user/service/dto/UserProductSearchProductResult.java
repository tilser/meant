package com.meant.api.module.user.service.dto;

import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
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
        Long listPriceAmount,
        String listPriceCurrency,
        Double ratingScore,
        Integer reviewCount,
        List<ProductCatalogMedia> media,
        List<ProductCatalogCategory> categories,
        List<String> certifications,
        List<String> materials,
        List<String> skus,
        List<String> collections,
        List<ProductCatalogAttribute> attributes,
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
        List<String> missedFilterIds,
        UserInventoryRecommendationRelationship inventoryRelationship,
        UUID inventoryItemId,
        String inventoryItemName
) {

    public static UserProductSearchProductResult from(
            UserProductSearchResultItem item,
            UserProductRecommendationExplanationResult explanation
    ) {
        return from(item, explanation, RichCatalogData.empty());
    }

    public static UserProductSearchProductResult from(
            UserProductSearchResultItem item,
            UserProductRecommendationExplanationResult explanation,
            RichCatalogData richCatalogData
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
                item.getListPriceAmount(),
                item.getListPriceCurrency(),
                item.getRatingScore(),
                item.getReviewCount(),
                richCatalogData.media(),
                richCatalogData.categories(),
                richCatalogData.certifications(),
                richCatalogData.materials(),
                richCatalogData.skus(),
                richCatalogData.collections(),
                richCatalogData.attributes(),
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
                explanation.missedFilterIds(),
                inventoryRelationship(explanation),
                explanation.inventoryItemId(),
                explanation.inventoryItemName()
        );
    }

    public UserProductSearchProductResult withMatchScore(int nextMatchScore) {
        return new UserProductSearchProductResult(
                productKey,
                productHash,
                merchantId,
                merchantDomain,
                merchantName,
                endpoint,
                merchantRank,
                merchantSemanticScore,
                merchantRerankScore,
                productId,
                title,
                descriptionHtml,
                url,
                imageUrl,
                priceMinAmount,
                priceMaxAmount,
                priceCurrency,
                listPriceAmount,
                listPriceCurrency,
                ratingScore,
                reviewCount,
                media,
                categories,
                certifications,
                materials,
                skus,
                collections,
                attributes,
                available,
                detailError,
                detailDescription,
                detailImageUrl,
                detailPriceMin,
                detailPriceMax,
                detailPriceCurrency,
                selectedVariantId,
                selectedVariantTitle,
                selectedVariantPriceAmount,
                selectedVariantPriceCurrency,
                selectedVariantImageUrl,
                selectedVariantImageAltText,
                selectedVariantAvailable,
                catalogRank,
                productRerankScore,
                rank,
                nextMatchScore,
                whyMeantForYou,
                matchedFilterIds,
                missedFilterIds,
                inventoryRelationship,
                inventoryItemId,
                inventoryItemName
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
        int inventoryAdjustment = switch (inventoryRelationship(explanation)) {
            case RESTOCK -> 8;
            case COMPLEMENT -> 5;
            case DUPLICATE -> -22;
            case NONE -> 0;
        };
        return Math.max(25, Math.min(99, base + preferenceBoost - missPenalty - rankPenalty + inventoryAdjustment));
    }

    private static UserInventoryRecommendationRelationship inventoryRelationship(
            UserProductRecommendationExplanationResult explanation
    ) {
        return explanation.inventoryRelationship() == null
                ? UserInventoryRecommendationRelationship.NONE
                : explanation.inventoryRelationship();
    }

    public record RichCatalogData(
            List<ProductCatalogMedia> media,
            List<ProductCatalogCategory> categories,
            List<String> certifications,
            List<String> materials,
            List<String> skus,
            List<String> collections,
            List<ProductCatalogAttribute> attributes
    ) {

        static RichCatalogData empty() {
            return new RichCatalogData(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }
}
