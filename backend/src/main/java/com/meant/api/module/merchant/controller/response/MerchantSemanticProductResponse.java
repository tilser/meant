package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import java.util.List;
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
        List<ProductImageResponse> detailImages,
        List<ProductOptionResponse> detailOptions,
        String detailPriceMin,
        String detailPriceMax,
        String detailPriceCurrency,
        Integer totalVariants,
        Boolean requiresSellingPlan,
        List<Object> sellingPlanGroups,
        String selectedVariantId,
        String selectedVariantTitle,
        List<ProductSelectedOptionResponse> selectedOptions,
        String selectedVariantPriceAmount,
        String selectedVariantPriceCurrency,
        String selectedVariantImageUrl,
        String selectedVariantImageAltText,
        Boolean selectedVariantAvailable,
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
                result.sellingPlanGroups(),
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

    public record ProductImageResponse(
            String url,
            String altText
    ) {

        static ProductImageResponse from(ProductDetailsResponse.Image image) {
            return new ProductImageResponse(image.url(), image.altText());
        }
    }

    public record ProductOptionResponse(
            String name,
            List<String> values
    ) {

        static ProductOptionResponse from(ProductDetailsResponse.Option option) {
            return new ProductOptionResponse(option.name(), option.values());
        }
    }

    public record ProductSelectedOptionResponse(
            String name,
            String value
    ) {

        static ProductSelectedOptionResponse from(ProductDetailsResponse.SelectedOption selectedOption) {
            return new ProductSelectedOptionResponse(selectedOption.name(), selectedOption.value());
        }
    }
}
