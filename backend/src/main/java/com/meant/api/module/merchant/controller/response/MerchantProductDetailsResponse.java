package com.meant.api.module.merchant.controller.response;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MerchantProductDetailsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String endpoint,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String url,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductImageResponse> images,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductMediaResponse> media,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductOptionResponse> options,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer totalVariants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String priceMin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String priceMax,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String priceCurrency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean requiresSellingPlan,
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
        List<ProductSelectedOptionResponse> selectedOptions
) {

    public static MerchantProductDetailsResponse from(ProductDetailsResult result) {
        ProductDetailsResponse.Product product = result.product();
        ProductDetailsResponse.PriceRange priceRange = product.priceRange();
        ProductDetailsResponse.SelectedVariant selectedVariant = product.selectedOrFirstAvailableVariant();
        return new MerchantProductDetailsResponse(
                result.endpoint(),
                product.productId(),
                product.title(),
                product.description(),
                product.url(),
                product.imageUrl(),
                safeList(product.images()).stream().map(ProductImageResponse::from).toList(),
                safeList(product.media()).stream().map(ProductMediaResponse::from).toList(),
                safeList(product.options()).stream().map(ProductOptionResponse::from).toList(),
                product.totalVariants(),
                priceRange == null ? null : priceRange.min(),
                priceRange == null ? null : priceRange.max(),
                priceRange == null ? null : priceRange.currency(),
                product.requiresSellingPlan(),
                selectedVariant == null ? null : selectedVariant.variantId(),
                selectedVariant == null ? null : selectedVariant.title(),
                selectedVariant == null ? null : selectedVariant.price(),
                selectedVariant == null ? null : selectedVariant.currency(),
                selectedVariant == null ? null : selectedVariant.imageUrl(),
                selectedVariant == null ? null : selectedVariant.imageAltText(),
                selectedVariant == null ? null : selectedVariant.available(),
                selectedVariant == null
                        ? List.of()
                        : safeList(selectedVariant.selectedOptions()).stream()
                                .map(ProductSelectedOptionResponse::from)
                                .toList()
        );
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

    public record ProductMediaResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String type,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String altText,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String previewImageUrl
    ) {

        static ProductMediaResponse from(ProductDetailsResponse.Media media) {
            return new ProductMediaResponse(media.type(), media.url(), media.altText(), media.previewImageUrl());
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
}
