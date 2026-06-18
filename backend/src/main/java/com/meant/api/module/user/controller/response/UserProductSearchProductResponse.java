package com.meant.api.module.user.controller.response;

import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import java.util.List;
import java.util.UUID;

public record UserProductSearchProductResponse(
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
        List<ProductMediaResponse> media,
        List<ProductCategoryResponse> categories,
        List<String> certifications,
        List<String> materials,
        List<String> skus,
        List<String> collections,
        List<ProductAttributeResponse> attributes,
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

    public static UserProductSearchProductResponse from(UserProductSearchProductResult result) {
        return new UserProductSearchProductResponse(
                result.productKey(),
                result.productHash(),
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
                result.listPriceAmount(),
                result.listPriceCurrency(),
                result.ratingScore(),
                result.reviewCount(),
                result.media().stream().map(ProductMediaResponse::from).toList(),
                result.categories().stream().map(ProductCategoryResponse::from).toList(),
                result.certifications(),
                result.materials(),
                result.skus(),
                result.collections(),
                result.attributes().stream().map(ProductAttributeResponse::from).toList(),
                result.available(),
                result.detailError(),
                result.detailDescription(),
                result.detailImageUrl(),
                result.detailPriceMin(),
                result.detailPriceMax(),
                result.detailPriceCurrency(),
                result.selectedVariantId(),
                result.selectedVariantTitle(),
                result.selectedVariantPriceAmount(),
                result.selectedVariantPriceCurrency(),
                result.selectedVariantImageUrl(),
                result.selectedVariantImageAltText(),
                result.selectedVariantAvailable(),
                result.catalogRank(),
                result.productRerankScore(),
                result.rank(),
                result.matchScore(),
                result.whyMeantForYou(),
                result.matchedFilterIds(),
                result.missedFilterIds(),
                result.inventoryRelationship(),
                result.inventoryItemId(),
                result.inventoryItemName()
        );
    }

    public record ProductMediaResponse(
            String type,
            String url,
            String altText
    ) {

        static ProductMediaResponse from(ProductCatalogMedia media) {
            return new ProductMediaResponse(media.type(), media.url(), media.altText());
        }
    }

    public record ProductCategoryResponse(
            String value,
            String taxonomy
    ) {

        static ProductCategoryResponse from(ProductCatalogCategory category) {
            return new ProductCategoryResponse(category.value(), category.taxonomy());
        }
    }

    public record ProductAttributeResponse(
            String name,
            String value
    ) {

        static ProductAttributeResponse from(ProductCatalogAttribute attribute) {
            return new ProductAttributeResponse(attribute.name(), attribute.value());
        }
    }
}
