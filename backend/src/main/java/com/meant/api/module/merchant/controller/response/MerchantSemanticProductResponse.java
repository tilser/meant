package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
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
        BuyerTextContext buyerText = new BuyerTextContext(result.merchantDomain(), result.endpoint());
        return new MerchantSemanticProductResponse(
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
                result.detailImages().stream().map(image -> ProductImageResponse.from(image, buyerText)).toList(),
                result.detailOptions().stream().map(option -> ProductOptionResponse.from(option, buyerText)).toList(),
                buyerText.text(result.detailPriceMin()),
                buyerText.text(result.detailPriceMax()),
                buyerText.text(result.detailPriceCurrency()),
                result.totalVariants(),
                result.requiresSellingPlan(),
                result.sellingPlanGroups().stream()
                        .map(group -> SellingPlanGroupResponse.from(group, buyerText))
                        .toList(),
                result.selectedVariantId(),
                buyerText.text(result.selectedVariantTitle()),
                result.selectedOptions().stream()
                        .map(option -> ProductSelectedOptionResponse.from(option, buyerText))
                        .toList(),
                buyerText.text(result.selectedVariantPriceAmount()),
                buyerText.text(result.selectedVariantPriceCurrency()),
                buyerText.url(result.selectedVariantImageUrl()),
                buyerText.text(result.selectedVariantImageAltText()),
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

    public record ProductImageResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String altText
    ) {

        static ProductImageResponse from(ProductDetailsResponse.Image image, BuyerTextContext buyerText) {
            return new ProductImageResponse(
                    buyerText.url(image.url()),
                    buyerText.text(image.altText())
            );
        }
    }

    public record ProductOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> values
    ) {

        static ProductOptionResponse from(ProductDetailsResponse.Option option, BuyerTextContext buyerText) {
            return new ProductOptionResponse(
                    buyerText.text(option.name()),
                    buyerText.texts(option.values())
            );
        }
    }

    public record ProductSelectedOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static ProductSelectedOptionResponse from(
                ProductDetailsResponse.SelectedOption selectedOption,
                BuyerTextContext buyerText
        ) {
            return new ProductSelectedOptionResponse(
                    buyerText.text(selectedOption.name()),
                    buyerText.text(selectedOption.value())
            );
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

        static SellingPlanGroupResponse from(ProductSellingPlanGroup group, BuyerTextContext buyerText) {
            return new SellingPlanGroupResponse(
                    group.id(),
                    buyerText.text(group.name()),
                    buyerText.text(group.appName()),
                    group.options().stream()
                            .map(option -> SellingPlanGroupOptionResponse.from(option, buyerText))
                            .toList(),
                    group.sellingPlans().stream()
                            .map(plan -> SellingPlanResponse.from(plan, buyerText))
                            .toList()
            );
        }
    }

    public record SellingPlanGroupOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> values
    ) {

        static SellingPlanGroupOptionResponse from(
                ProductSellingPlanGroup.GroupOption option,
                BuyerTextContext buyerText
        ) {
            return new SellingPlanGroupOptionResponse(
                    buyerText.text(option.name()),
                    buyerText.texts(option.values())
            );
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

        static SellingPlanResponse from(
                ProductSellingPlanGroup.SellingPlan plan,
                BuyerTextContext buyerText
        ) {
            return new SellingPlanResponse(
                    plan.id(),
                    buyerText.text(plan.name()),
                    buyerText.text(plan.description()),
                    plan.options().stream()
                            .map(option -> SellingPlanOptionResponse.from(option, buyerText))
                            .toList()
            );
        }
    }

    public record SellingPlanOptionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {

        static SellingPlanOptionResponse from(
                ProductSellingPlanGroup.Option option,
                BuyerTextContext buyerText
        ) {
            return new SellingPlanOptionResponse(
                    buyerText.text(option.name()),
                    buyerText.text(option.value())
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
