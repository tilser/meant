package com.meant.api.module.merchant.controller.response;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import java.util.List;

public record MerchantProductDetailsResponse(
        String endpoint,
        String productId,
        String title,
        String description,
        String url,
        String imageUrl,
        List<ProductImageResponse> images,
        List<ProductMediaResponse> media,
        List<ProductOptionResponse> options,
        Integer totalVariants,
        String priceMin,
        String priceMax,
        String priceCurrency,
        Boolean requiresSellingPlan,
        String selectedVariantId,
        String selectedVariantTitle,
        String selectedVariantPriceAmount,
        String selectedVariantPriceCurrency,
        String selectedVariantImageUrl,
        String selectedVariantImageAltText,
        Boolean selectedVariantAvailable,
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
            String url,
            String altText
    ) {

        static ProductImageResponse from(ProductDetailsResponse.Image image) {
            return new ProductImageResponse(image.url(), image.altText());
        }
    }

    public record ProductMediaResponse(
            String type,
            String url,
            String altText,
            String previewImageUrl
    ) {

        static ProductMediaResponse from(ProductDetailsResponse.Media media) {
            return new ProductMediaResponse(media.type(), media.url(), media.altText(), media.previewImageUrl());
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
