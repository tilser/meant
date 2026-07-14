package com.meant.api.module.user.controller.response;

import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Full current provider detail. This response is transient and is never read from saved display data. */
@Schema(name = "UserSavedProductDetails")
public record UserSavedProductDetailsResponse(
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String productId,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String handle,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String title,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String description,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String url,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Image> images,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Media> media,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Category> categories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> tags,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Option> options,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Variant> variants,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer totalVariants,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String priceMin,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String priceMax,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String priceCurrency,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String listPriceMin,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String listPriceMax,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String listPriceCurrency,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean requiresSellingPlan,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String selectedVariantId,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String selectedVariantTitle,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String selectedVariantPriceAmount,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String selectedVariantPriceCurrency,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String selectedVariantSku,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String selectedVariantListPriceAmount,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String selectedVariantListPriceCurrency,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String selectedVariantImageUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String selectedVariantImageAltText,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean selectedVariantAvailable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SelectedOption> selectedOptions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> skus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> certifications,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> materials,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> collections,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Attribute> attributes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Message> messages,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Double ratingScore,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Double ratingScaleMax,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long reviewCount,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String merchantName
) {

    public static UserSavedProductDetailsResponse from(RehydratedProductDetails details) {
        RehydratedProductDetails.PriceRange price = details.priceRange();
        RehydratedProductDetails.PriceRange listPrice = details.listPriceRange();
        RehydratedProductDetails.Variant selected = details.selectedVariant();
        return new UserSavedProductDetailsResponse(
                details.productId(),
                details.handle(),
                details.title(),
                details.description(),
                details.url(),
                details.imageUrl(),
                details.images().stream().map(Image::from).toList(),
                details.media().stream().map(Media::from).toList(),
                details.categories().stream().map(Category::from).toList(),
                details.tags(),
                details.options().stream().map(Option::from).toList(),
                details.variants().stream().map(Variant::from).toList(),
                details.totalVariants(),
                price == null ? null : price.min(),
                price == null ? null : price.max(),
                price == null ? null : price.currency(),
                listPrice == null ? null : listPrice.min(),
                listPrice == null ? null : listPrice.max(),
                listPrice == null ? null : listPrice.currency(),
                details.requiresSellingPlan(),
                selected == null ? null : selected.variantId(),
                selected == null ? null : selected.title(),
                selected == null ? null : selected.priceAmount(),
                selected == null ? null : selected.priceCurrency(),
                selected == null ? null : selected.sku(),
                selected == null ? null : selected.listPriceAmount(),
                selected == null ? null : selected.listPriceCurrency(),
                selected == null ? null : selected.imageUrl(),
                selected == null ? null : selected.imageAltText(),
                selected == null ? null : selected.available(),
                selected == null
                        ? List.of()
                        : selected.selectedOptions().stream().map(SelectedOption::from).toList(),
                details.skus(),
                details.certifications(),
                details.materials(),
                details.collections(),
                details.attributes().stream().map(Attribute::from).toList(),
                details.messages().stream().map(Message::from).toList(),
                details.ratingScore(),
                details.ratingScaleMax(),
                details.reviewCount(),
                details.merchantName()
        );
    }

    @Schema(name = "UserSavedProductDetailImage")
    public record Image(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String url,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String altText
    ) {
        private static Image from(RehydratedProductDetails.Image image) {
            return new Image(image.url(), image.altText());
        }
    }

    @Schema(name = "UserSavedProductDetailMedia")
    public record Media(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String type,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String url,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String altText,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String previewImageUrl
    ) {
        private static Media from(RehydratedProductDetails.Media media) {
            return new Media(media.type(), media.url(), media.altText(), media.previewImageUrl());
        }
    }

    @Schema(name = "UserSavedProductDetailCategory")
    public record Category(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String value,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String taxonomy
    ) {
        private static Category from(RehydratedProductDetails.Category category) {
            return new Category(category.value(), category.taxonomy());
        }
    }

    @Schema(name = "UserSavedProductDetailOption")
    public record Option(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> values
    ) {
        private static Option from(RehydratedProductDetails.Option option) {
            return new Option(option.name(), option.values());
        }
    }

    @Schema(name = "UserSavedProductDetailSelectedOption")
    public record SelectedOption(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String name,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String value
    ) {
        private static SelectedOption from(RehydratedProductDetails.SelectedOption option) {
            return new SelectedOption(option.name(), option.value());
        }
    }

    @Schema(name = "UserSavedProductDetailAttribute")
    public record Attribute(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String name,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String value
    ) {
        private static Attribute from(RehydratedProductDetails.Attribute attribute) {
            return new Attribute(attribute.name(), attribute.value());
        }
    }

    @Schema(name = "UserSavedProductDetailVariant")
    public record Variant(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String variantId,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String handle,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String title,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String description,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String url,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String priceAmount,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String priceCurrency,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String listPriceAmount,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String listPriceCurrency,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String sku,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String imageUrl,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String imageAltText,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<Media> media,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Boolean available,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<SelectedOption> selectedOptions,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<Category> categories,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> tags,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<Attribute> attributes
    ) {
        private static Variant from(RehydratedProductDetails.Variant variant) {
            return new Variant(
                    variant.variantId(),
                    variant.handle(),
                    variant.title(),
                    variant.description(),
                    variant.url(),
                    variant.priceAmount(),
                    variant.priceCurrency(),
                    variant.listPriceAmount(),
                    variant.listPriceCurrency(),
                    variant.sku(),
                    variant.imageUrl(),
                    variant.imageAltText(),
                    variant.media().stream().map(Media::from).toList(),
                    variant.available(),
                    variant.selectedOptions().stream().map(SelectedOption::from).toList(),
                    variant.categories().stream().map(Category::from).toList(),
                    variant.tags(),
                    variant.attributes().stream().map(Attribute::from).toList()
            );
        }
    }

    @Schema(name = "UserSavedProductDetailMessage")
    public record Message(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String type,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String code,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String path,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String contentType,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String content,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String severity,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String presentation,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String imageUrl,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String url
    ) {
        private static Message from(RehydratedProductDetails.Message message) {
            return new Message(
                    message.type(),
                    message.code(),
                    message.path(),
                    message.contentType(),
                    message.content(),
                    message.severity(),
                    message.presentation(),
                    message.imageUrl(),
                    message.url()
            );
        }
    }
}
