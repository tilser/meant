package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
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
