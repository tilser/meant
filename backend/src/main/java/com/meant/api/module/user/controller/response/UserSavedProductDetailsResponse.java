package com.meant.api.module.user.controller.response;

import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import com.meant.api.module.merchant.service.MerchantProductMessageSanitizer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

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
        String merchantName,
        @Schema(
                description = "Verified official storefront origin for buyer display",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String merchantOrigin
) {

    public static UserSavedProductDetailsResponse from(RehydratedProductDetails details) {
        MerchantProductMessageSanitizer.TransportContext buyerContext =
                MerchantProductMessageSanitizer.context(
                        details.merchantOrigin(),
                        null,
                        details.technicalEndpointAliases().toArray(String[]::new)
                );
        RehydratedProductDetails.PriceRange price = details.priceRange();
        RehydratedProductDetails.PriceRange listPrice = details.listPriceRange();
        RehydratedProductDetails.Variant selected = details.selectedVariant();
        return new UserSavedProductDetailsResponse(
                details.productId(),
                details.handle(),
                buyerText(details.title(), buyerContext),
                buyerText(details.description(), buyerContext),
                buyerUrl(details.url(), buyerContext),
                buyerUrl(details.imageUrl(), buyerContext),
                details.images().stream()
                        .filter(Objects::nonNull)
                        .map(image -> Image.from(image, buyerContext))
                        .toList(),
                details.media().stream()
                        .filter(Objects::nonNull)
                        .map(media -> Media.from(media, buyerContext))
                        .toList(),
                details.categories().stream()
                        .filter(Objects::nonNull)
                        .map(category -> Category.from(category, buyerContext))
                        .toList(),
                buyerTextValues(details.tags(), buyerContext),
                details.options().stream()
                        .filter(Objects::nonNull)
                        .map(option -> Option.from(option, buyerContext))
                        .toList(),
                details.variants().stream()
                        .filter(Objects::nonNull)
                        .map(variant -> Variant.from(variant, buyerContext))
                        .toList(),
                details.totalVariants(),
                price == null ? null : price.min(),
                price == null ? null : price.max(),
                price == null ? null : price.currency(),
                listPrice == null ? null : listPrice.min(),
                listPrice == null ? null : listPrice.max(),
                listPrice == null ? null : listPrice.currency(),
                details.requiresSellingPlan(),
                selected == null ? null : selected.variantId(),
                selected == null ? null : buyerText(selected.title(), buyerContext),
                selected == null ? null : selected.priceAmount(),
                selected == null ? null : selected.priceCurrency(),
                selected == null ? null : buyerText(selected.sku(), buyerContext),
                selected == null ? null : selected.listPriceAmount(),
                selected == null ? null : selected.listPriceCurrency(),
                selected == null ? null : buyerUrl(selected.imageUrl(), buyerContext),
                selected == null ? null : buyerText(selected.imageAltText(), buyerContext),
                selected == null ? null : selected.available(),
                !details.selected().isEmpty()
                        ? details.selected().stream()
                                .filter(Objects::nonNull)
                                .map(option -> SelectedOption.from(option, buyerContext))
                                .toList()
                        : selected == null
                                ? List.of()
                                : selected.selectedOptions().stream()
                                        .filter(Objects::nonNull)
                                        .map(option -> SelectedOption.from(option, buyerContext))
                                        .toList(),
                buyerTextValues(details.skus(), buyerContext),
                buyerTextValues(details.certifications(), buyerContext),
                buyerTextValues(details.materials(), buyerContext),
                buyerTextValues(details.collections(), buyerContext),
                details.attributes().stream()
                        .filter(Objects::nonNull)
                        .map(attribute -> Attribute.from(attribute, buyerContext))
                        .filter(attribute -> hasText(attribute.name()) && hasText(attribute.value()))
                        .toList(),
                details.messages().stream()
                        .filter(Objects::nonNull)
                        .map(message -> Message.from(message, buyerContext))
                        .toList(),
                details.ratingScore(),
                details.ratingScaleMax(),
                details.reviewCount(),
                buyerText(details.merchantName(), buyerContext),
                MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(details.merchantOrigin())
        );
    }

    @Schema(name = "UserSavedProductDetailImage")
    public record Image(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String url,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String altText
    ) {
        private static Image from(
                RehydratedProductDetails.Image image,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new Image(
                    buyerUrl(image.url(), context),
                    buyerText(image.altText(), context)
            );
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
        private static Media from(
                RehydratedProductDetails.Media media,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new Media(
                    MerchantProductMessageSanitizer.buyerSafeMediaType(media.type(), context),
                    buyerUrl(media.url(), context),
                    buyerText(media.altText(), context),
                    buyerUrl(media.previewImageUrl(), context)
            );
        }
    }

    @Schema(name = "UserSavedProductDetailCategory")
    public record Category(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String value,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String taxonomy
    ) {
        private static Category from(
                RehydratedProductDetails.Category category,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new Category(
                    buyerText(category.value(), context),
                    buyerText(category.taxonomy(), context)
            );
        }
    }

    @Schema(name = "UserSavedProductDetailOption")
    public record Option(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> values,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<OptionValue> valueDetails
    ) {
        private static Option from(
                RehydratedProductDetails.Option option,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new Option(
                    buyerText(option.name(), context),
                    buyerTextValues(option.values(), context),
                    option.valueDetails().stream()
                            .filter(Objects::nonNull)
                            .map(value -> OptionValue.from(value, context))
                            .toList()
            );
        }
    }

    @Schema(name = "UserSavedProductDetailOptionValue")
    public record OptionValue(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String value,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Boolean available,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Boolean exists
    ) {
        private static OptionValue from(
                RehydratedProductDetails.OptionValue value,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new OptionValue(
                    buyerText(value.value(), context),
                    value.available(),
                    value.exists()
            );
        }
    }

    @Schema(name = "UserSavedProductDetailSelectedOption")
    public record SelectedOption(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String name,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String value
    ) {
        private static SelectedOption from(
                RehydratedProductDetails.SelectedOption option,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new SelectedOption(
                    buyerText(option.name(), context),
                    buyerText(option.value(), context)
            );
        }
    }

    @Schema(name = "UserSavedProductDetailAttribute")
    public record Attribute(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String name,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String value
    ) {
        private static Attribute from(
                RehydratedProductDetails.Attribute attribute,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new Attribute(
                    buyerText(attribute.name(), context),
                    buyerText(attribute.value(), context)
            );
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
        private static Variant from(
                RehydratedProductDetails.Variant variant,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new Variant(
                    variant.variantId(),
                    variant.handle(),
                    buyerText(variant.title(), context),
                    buyerText(variant.description(), context),
                    buyerUrl(variant.url(), context),
                    variant.priceAmount(),
                    variant.priceCurrency(),
                    variant.listPriceAmount(),
                    variant.listPriceCurrency(),
                    buyerText(variant.sku(), context),
                    buyerUrl(variant.imageUrl(), context),
                    buyerText(variant.imageAltText(), context),
                    variant.media().stream()
                            .filter(Objects::nonNull)
                            .map(media -> Media.from(media, context))
                            .toList(),
                    variant.available(),
                    variant.selectedOptions().stream()
                            .filter(Objects::nonNull)
                            .map(option -> SelectedOption.from(option, context))
                            .toList(),
                    variant.categories().stream()
                            .filter(Objects::nonNull)
                            .map(category -> Category.from(category, context))
                            .toList(),
                    buyerTextValues(variant.tags(), context),
                    variant.attributes().stream()
                            .filter(Objects::nonNull)
                            .map(attribute -> Attribute.from(attribute, context))
                            .filter(attribute -> hasText(attribute.name()) && hasText(attribute.value()))
                            .toList()
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
        private static Message from(
                RehydratedProductDetails.Message message,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            MerchantProductMessageSanitizer.SanitizedMessage sanitized =
                    MerchantProductMessageSanitizer.sanitize(
                            message.type(),
                            message.code(),
                            message.path(),
                            message.contentType(),
                            message.content(),
                            message.severity(),
                            message.presentation(),
                            message.imageUrl(),
                            message.url(),
                            context
                    );
            return new Message(
                    sanitized.type(),
                    sanitized.code(),
                    sanitized.path(),
                    sanitized.contentType(),
                    sanitized.content(),
                    sanitized.severity(),
                    sanitized.presentation(),
                    sanitized.imageUrl(),
                    sanitized.url()
            );
        }
    }

    private static String buyerText(
            String value,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        return MerchantProductMessageSanitizer.sanitizeBuyerText(value, context);
    }

    private static String buyerUrl(
            String value,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        return MerchantProductMessageSanitizer.buyerSafeUrl(value, context);
    }

    private static List<String> buyerTextValues(
            List<String> values,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(value -> buyerText(value, context))
                .filter(UserSavedProductDetailsResponse::hasText)
                .distinct()
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
