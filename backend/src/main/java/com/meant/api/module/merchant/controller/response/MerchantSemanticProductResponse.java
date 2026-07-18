package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.merchant.service.dto.ProductSellingPlanGroup;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record MerchantSemanticProductResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String merchantDomain,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String merchantName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String endpoint,
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
        List<ProductImageResponse> detailImages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductOptionResponse> detailOptions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detailPriceMin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detailPriceMax,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detailPriceCurrency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer totalVariants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean requiresSellingPlan,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SellingPlanGroupResponse> sellingPlanGroups,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedVariantTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductSelectedOptionResponse> selectedOptions,
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
                result.detailImages().stream().map(ProductImageResponse::from).toList(),
                result.detailOptions().stream().map(ProductOptionResponse::from).toList(),
                result.detailPriceMin(),
                result.detailPriceMax(),
                result.detailPriceCurrency(),
                result.totalVariants(),
                result.requiresSellingPlan(),
                result.sellingPlanGroups().stream().map(SellingPlanGroupResponse::from).toList(),
                result.selectedVariantId(),
                result.selectedVariantTitle(),
                result.selectedOptions().stream().map(ProductSelectedOptionResponse::from).toList(),
                result.selectedVariantPriceAmount(),
                result.selectedVariantPriceCurrency(),
                result.selectedVariantImageUrl(),
                result.selectedVariantImageAltText(),
                result.selectedVariantAvailable(),
                result.catalogRank(),
                result.productRerankScore(),
                result.rank()
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

        static ProductMediaResponse from(ProductCatalogMedia media) {
            return new ProductMediaResponse(media.type(), media.url(), media.altText());
        }
    }

    public record ProductCategoryResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String taxonomy
    ) {

        static ProductCategoryResponse from(ProductCatalogCategory category) {
            return new ProductCategoryResponse(category.value(), category.taxonomy());
        }
    }

    public record ProductAttributeResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static ProductAttributeResponse from(ProductCatalogAttribute attribute) {
            return new ProductAttributeResponse(attribute.name(), attribute.value());
        }
    }

    public record ProductImageResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String altText
    ) {

        static ProductImageResponse from(ProductDetailsResponse.Image image) {
            return new ProductImageResponse(image.url(), image.altText());
        }
    }

    public record ProductOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> values
    ) {

        static ProductOptionResponse from(ProductDetailsResponse.Option option) {
            return new ProductOptionResponse(option.name(), option.values());
        }
    }

    public record ProductSelectedOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static ProductSelectedOptionResponse from(ProductDetailsResponse.SelectedOption selectedOption) {
            return new ProductSelectedOptionResponse(selectedOption.name(), selectedOption.value());
        }
    }

    public record SellingPlanGroupResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String appName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<SellingPlanGroupOptionResponse> options,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<SellingPlanResponse> sellingPlans
    ) {

        static SellingPlanGroupResponse from(ProductSellingPlanGroup group) {
            return new SellingPlanGroupResponse(
                    group.id(),
                    group.name(),
                    group.appName(),
                    group.options().stream().map(SellingPlanGroupOptionResponse::from).toList(),
                    group.sellingPlans().stream().map(SellingPlanResponse::from).toList()
            );
        }
    }

    public record SellingPlanGroupOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> values
    ) {

        static SellingPlanGroupOptionResponse from(ProductSellingPlanGroup.GroupOption option) {
            return new SellingPlanGroupOptionResponse(option.name(), option.values());
        }
    }

    public record SellingPlanResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String description,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<SellingPlanOptionResponse> options
    ) {

        static SellingPlanResponse from(ProductSellingPlanGroup.SellingPlan plan) {
            return new SellingPlanResponse(
                    plan.id(),
                    plan.name(),
                    plan.description(),
                    plan.options().stream().map(SellingPlanOptionResponse::from).toList()
            );
        }
    }

    public record SellingPlanOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static SellingPlanOptionResponse from(ProductSellingPlanGroup.Option option) {
            return new SellingPlanOptionResponse(option.name(), option.value());
        }
    }
}
