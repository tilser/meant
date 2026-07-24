package com.meant.api.module.user.controller.response;

import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record UserProductSearchProductResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String merchantDomain,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String merchantName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int merchantRank,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double merchantSemanticScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double merchantRerankScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String descriptionHtml,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String url,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long priceMinAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long priceMaxAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String priceCurrency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long listPriceAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String listPriceCurrency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Double ratingScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer reviewCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductMediaResponse> media,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductCategoryResponse> categories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> certifications,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> materials,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> skus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> collections,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductAttributeResponse> attributes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean available,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detailError,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detailDescription,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detailImageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detailPriceMin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detailPriceMax,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detailPriceCurrency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantPriceAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantPriceCurrency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantImageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantImageAltText,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean selectedVariantAvailable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int catalogRank,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double productRerankScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int rank,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int matchScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String whyMeantForYou,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> matchedFilterIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> missedFilterIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserInventoryRecommendationRelationship inventoryRelationship,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID inventoryItemId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String inventoryItemName
) {

    public static UserProductSearchProductResponse from(UserProductSearchProductResult result) {
        BuyerTextContext buyerText = new BuyerTextContext(result.merchantDomain(), result.endpoint());
        return new UserProductSearchProductResponse(
                result.productKey(),
                result.productHash(),
                result.merchantId(),
                result.merchantDomain(),
                buyerText.text(result.merchantName()),
                result.merchantRank(),
                result.merchantSemanticScore(),
                result.merchantRerankScore(),
                result.productId(),
                buyerText.text(result.title()),
                buyerText.text(result.descriptionHtml()),
                buyerText.url(result.url()),
                buyerText.url(result.imageUrl()),
                result.priceMinAmount(),
                result.priceMaxAmount(),
                buyerText.text(result.priceCurrency()),
                result.listPriceAmount(),
                buyerText.text(result.listPriceCurrency()),
                result.ratingScore(),
                result.reviewCount(),
                result.media().stream().map(media -> ProductMediaResponse.from(media, buyerText)).toList(),
                result.categories().stream().map(category -> ProductCategoryResponse.from(category, buyerText)).toList(),
                buyerText.texts(result.certifications()),
                buyerText.texts(result.materials()),
                buyerText.texts(result.skus()),
                buyerText.texts(result.collections()),
                result.attributes().stream()
                        .map(attribute -> ProductAttributeResponse.from(attribute, buyerText))
                        .toList(),
                result.available(),
                buyerText.text(result.detailError()),
                buyerText.text(result.detailDescription()),
                buyerText.url(result.detailImageUrl()),
                buyerText.text(result.detailPriceMin()),
                buyerText.text(result.detailPriceMax()),
                buyerText.text(result.detailPriceCurrency()),
                result.selectedVariantId(),
                buyerText.text(result.selectedVariantTitle()),
                buyerText.text(result.selectedVariantPriceAmount()),
                buyerText.text(result.selectedVariantPriceCurrency()),
                buyerText.url(result.selectedVariantImageUrl()),
                buyerText.text(result.selectedVariantImageAltText()),
                result.selectedVariantAvailable(),
                result.catalogRank(),
                result.productRerankScore(),
                result.rank(),
                result.matchScore(),
                buyerText.text(result.whyMeantForYou()),
                result.matchedFilterIds(),
                result.missedFilterIds(),
                result.inventoryRelationship(),
                result.inventoryItemId(),
                buyerText.text(result.inventoryItemName())
        );
    }

    public record ProductMediaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String type,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String altText
    ) {

        static ProductMediaResponse from(ProductCatalogMedia media, BuyerTextContext buyerText) {
            return new ProductMediaResponse(
                    buyerText.text(media.type()),
                    buyerText.url(media.url()),
                    buyerText.text(media.altText())
            );
        }
    }

    public record ProductCategoryResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String taxonomy
    ) {

        static ProductCategoryResponse from(ProductCatalogCategory category, BuyerTextContext buyerText) {
            return new ProductCategoryResponse(
                    buyerText.text(category.value()),
                    buyerText.text(category.taxonomy())
            );
        }
    }

    public record ProductAttributeResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static ProductAttributeResponse from(ProductCatalogAttribute attribute, BuyerTextContext buyerText) {
            return new ProductAttributeResponse(
                    buyerText.text(attribute.name()),
                    buyerText.text(attribute.value())
            );
        }
    }

    private record BuyerTextContext(String merchantDomain, String endpoint) {

        private String text(String value) {
            return MerchantBuyerTextSanitizer.sanitize(
                    value,
                    merchantDomain,
                    endpoint,
                    endpoint
            );
        }

        private List<String> texts(List<String> values) {
            return values == null ? null : values.stream().map(this::text).toList();
        }

        private String url(String value) {
            if (value == null || value.isBlank()) {
                return value;
            }
            String trimmed = value.trim();
            return trimmed.equals(text(trimmed)) ? trimmed : null;
        }
    }
}
