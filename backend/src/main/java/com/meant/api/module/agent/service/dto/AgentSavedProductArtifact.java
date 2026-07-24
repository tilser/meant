package com.meant.api.module.agent.service.dto;

import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import java.time.Instant;
import java.util.List;

/**
 * Durable buyer-visible saved-product state. Technical provider routing data is deliberately omitted.
 */
public record AgentSavedProductArtifact(
        String id,
        String productHash,
        String name,
        String brand,
        String category,
        String tone,
        String imageUrl,
        String productUrl,
        Boolean remote,
        Integer match,
        Double priceFrom,
        Long priceFromMinorUnits,
        String priceCurrency,
        Integer merchants,
        List<String> satisfies,
        List<String> misses,
        String note,
        List<String> pros,
        List<String> cons,
        UserSavedProductResult.Review review,
        List<AgentSavedProductOfferResult> offers,
        String needs,
        List<String> provides,
        String marketCountry,
        boolean marketContextApplied,
        boolean commercialFactsAuthoritative,
        RehydratedProductDetails details,
        Instant createdAt,
        Instant updatedAt
) {

    public AgentSavedProductArtifact {
        satisfies = immutable(satisfies);
        misses = immutable(misses);
        pros = immutable(pros);
        cons = immutable(cons);
        offers = immutable(offers);
        provides = immutable(provides);
    }

    public static AgentSavedProductArtifact from(UserSavedProductResult product) {
        String merchantOrigin = product.offers().stream()
                .map(UserSavedProductResult.Offer::merchantOrigin)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> product.details() != null && product.details().merchantOrigin() != null
                        ? product.details().merchantOrigin()
                        : product.merchantOrigin());
        List<String> technicalAliases = java.util.stream.Stream.concat(
                        product.technicalEndpointAliases().stream(),
                        product.details() == null
                                ? java.util.stream.Stream.empty()
                                : product.details().technicalEndpointAliases().stream()
                )
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        AgentBuyerDisplayText.Context context =
                AgentBuyerDisplayText.context(merchantOrigin, technicalAliases);
        return new AgentSavedProductArtifact(
                product.id(),
                product.productHash(),
                AgentBuyerDisplayText.label(product.name(), context),
                AgentBuyerDisplayText.label(product.brand(), context),
                AgentBuyerDisplayText.label(product.category(), context),
                product.tone(),
                AgentBuyerDisplayText.safeUrl(product.imageUrl(), context),
                AgentBuyerDisplayText.safeUrl(product.productUrl(), context),
                product.remote(),
                product.match(),
                product.priceFrom(),
                product.priceFromMinorUnits(),
                product.priceCurrency(),
                product.merchants(),
                sanitizedStrings(product.satisfies(), context),
                sanitizedStrings(product.misses(), context),
                AgentBuyerDisplayText.text(product.note(), context),
                sanitizedStrings(product.pros(), context),
                sanitizedStrings(product.cons(), context),
                sanitizedReview(product.review(), context),
                product.offers().stream()
                        .map(offer -> AgentSavedProductOfferResult.from(
                                offer,
                                AgentBuyerDisplayText.context(
                                        offer.merchantOrigin(),
                                        technicalAliases
                                )
                        ))
                        .toList(),
                AgentBuyerDisplayText.text(product.needs(), context),
                sanitizedStrings(product.provides(), context),
                product.marketCountry(),
                product.marketContextApplied(),
                product.commercialFactsAuthoritative(),
                sanitizedDetails(product.details(), context),
                product.createdAt(),
                product.updatedAt()
        );
    }

    private static UserSavedProductResult.Review sanitizedReview(
            UserSavedProductResult.Review review,
            AgentBuyerDisplayText.Context context
    ) {
        return review == null
                ? null
                : new UserSavedProductResult.Review(
                        review.score(),
                        review.count(),
                        AgentBuyerDisplayText.text(review.insight(), context)
                );
    }

    private static RehydratedProductDetails sanitizedDetails(
            RehydratedProductDetails details,
            AgentBuyerDisplayText.Context parentContext
    ) {
        if (details == null) {
            return null;
        }
        List<String> technicalAliases = new java.util.ArrayList<>(parentContext.technicalAliases());
        technicalAliases.addAll(details.technicalEndpointAliases());
        AgentBuyerDisplayText.Context context = AgentBuyerDisplayText.context(
                details.merchantOrigin() == null
                        ? parentContext.merchantOrigin()
                        : details.merchantOrigin(),
                technicalAliases
        );
        return new RehydratedProductDetails(
                details.productId(),
                details.handle(),
                AgentBuyerDisplayText.label(details.title(), context),
                AgentBuyerDisplayText.text(details.description(), context),
                AgentBuyerDisplayText.safeUrl(details.url(), context),
                AgentBuyerDisplayText.safeUrl(details.imageUrl(), context),
                details.images().stream()
                        .map(image -> sanitizedImage(image, context))
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                details.media().stream()
                        .map(media -> sanitizedMedia(media, context))
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                details.categories().stream()
                        .map(category -> sanitizedCategory(category, context))
                        .toList(),
                sanitizedStrings(details.tags(), context),
                details.options().stream()
                        .map(option -> sanitizedOption(option, context))
                        .toList(),
                details.selected().stream()
                        .map(option -> sanitizedSelectedOption(option, context))
                        .toList(),
                details.variants().stream()
                        .map(variant -> sanitizedVariant(variant, context))
                        .toList(),
                details.totalVariants(),
                details.priceRange(),
                details.listPriceRange(),
                details.requiresSellingPlan(),
                details.selectedVariant() == null
                        ? null
                        : sanitizedVariant(details.selectedVariant(), context),
                sanitizedStrings(details.skus(), context),
                sanitizedStrings(details.certifications(), context),
                sanitizedStrings(details.materials(), context),
                sanitizedStrings(details.collections(), context),
                details.attributes().stream()
                        .map(attribute -> sanitizedAttribute(attribute, context))
                        .toList(),
                details.messages().stream()
                        .map(message -> sanitizedMessage(message, context))
                        .toList(),
                details.ratingScore(),
                details.ratingScaleMax(),
                details.reviewCount(),
                AgentBuyerDisplayText.label(details.merchantName(), context),
                context.merchantOrigin(),
                List.of()
        );
    }

    private static RehydratedProductDetails.Image sanitizedImage(
            RehydratedProductDetails.Image image,
            AgentBuyerDisplayText.Context context
    ) {
        String url = AgentBuyerDisplayText.safeUrl(image.url(), context);
        return url == null
                ? null
                : new RehydratedProductDetails.Image(
                        url,
                        AgentBuyerDisplayText.text(image.altText(), context)
                );
    }

    private static RehydratedProductDetails.Media sanitizedMedia(
            RehydratedProductDetails.Media media,
            AgentBuyerDisplayText.Context context
    ) {
        String url = AgentBuyerDisplayText.safeUrl(media.url(), context);
        return url == null
                ? null
                : new RehydratedProductDetails.Media(
                        AgentBuyerDisplayText.label(media.type(), context),
                        url,
                        AgentBuyerDisplayText.text(media.altText(), context),
                        AgentBuyerDisplayText.safeUrl(media.previewImageUrl(), context)
                );
    }

    private static RehydratedProductDetails.Category sanitizedCategory(
            RehydratedProductDetails.Category category,
            AgentBuyerDisplayText.Context context
    ) {
        return new RehydratedProductDetails.Category(
                AgentBuyerDisplayText.label(category.value(), context),
                AgentBuyerDisplayText.label(category.taxonomy(), context)
        );
    }

    private static RehydratedProductDetails.Option sanitizedOption(
            RehydratedProductDetails.Option option,
            AgentBuyerDisplayText.Context context
    ) {
        return new RehydratedProductDetails.Option(
                AgentBuyerDisplayText.label(option.name(), context),
                option.values().stream()
                        .map(value -> AgentBuyerDisplayText.label(value, context))
                        .toList(),
                option.valueDetails().stream()
                        .map(value -> new RehydratedProductDetails.OptionValue(
                                AgentBuyerDisplayText.label(value.value(), context),
                                value.available(),
                                value.exists()
                        ))
                        .toList()
        );
    }

    private static RehydratedProductDetails.SelectedOption sanitizedSelectedOption(
            RehydratedProductDetails.SelectedOption option,
            AgentBuyerDisplayText.Context context
    ) {
        return new RehydratedProductDetails.SelectedOption(
                AgentBuyerDisplayText.label(option.name(), context),
                AgentBuyerDisplayText.label(option.value(), context)
        );
    }

    private static RehydratedProductDetails.Variant sanitizedVariant(
            RehydratedProductDetails.Variant variant,
            AgentBuyerDisplayText.Context context
    ) {
        return new RehydratedProductDetails.Variant(
                variant.variantId(),
                variant.handle(),
                AgentBuyerDisplayText.label(variant.title(), context),
                AgentBuyerDisplayText.text(variant.description(), context),
                AgentBuyerDisplayText.safeUrl(variant.url(), context),
                variant.priceAmount(),
                variant.priceCurrency(),
                variant.listPriceAmount(),
                variant.listPriceCurrency(),
                AgentBuyerDisplayText.label(variant.sku(), context),
                AgentBuyerDisplayText.safeUrl(variant.imageUrl(), context),
                AgentBuyerDisplayText.text(variant.imageAltText(), context),
                variant.media().stream()
                        .map(media -> sanitizedMedia(media, context))
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                variant.available(),
                variant.selectedOptions().stream()
                        .map(option -> sanitizedSelectedOption(option, context))
                        .toList(),
                variant.categories().stream()
                        .map(category -> sanitizedCategory(category, context))
                        .toList(),
                sanitizedStrings(variant.tags(), context),
                variant.attributes().stream()
                        .map(attribute -> sanitizedAttribute(attribute, context))
                        .toList()
        );
    }

    private static RehydratedProductDetails.Attribute sanitizedAttribute(
            RehydratedProductDetails.Attribute attribute,
            AgentBuyerDisplayText.Context context
    ) {
        return new RehydratedProductDetails.Attribute(
                AgentBuyerDisplayText.label(attribute.name(), context),
                AgentBuyerDisplayText.label(attribute.value(), context)
        );
    }

    private static RehydratedProductDetails.Message sanitizedMessage(
            RehydratedProductDetails.Message message,
            AgentBuyerDisplayText.Context context
    ) {
        return new RehydratedProductDetails.Message(
                AgentBuyerDisplayText.label(message.type(), context),
                AgentBuyerDisplayText.label(message.code(), context),
                AgentBuyerDisplayText.text(message.path(), context),
                AgentBuyerDisplayText.label(message.contentType(), context),
                AgentBuyerDisplayText.text(message.content(), context),
                AgentBuyerDisplayText.label(message.severity(), context),
                AgentBuyerDisplayText.label(message.presentation(), context),
                AgentBuyerDisplayText.safeUrl(message.imageUrl(), context),
                AgentBuyerDisplayText.safeUrl(message.url(), context)
        );
    }

    private static List<String> sanitizedStrings(
            List<String> values,
            AgentBuyerDisplayText.Context context
    ) {
        return values.stream()
                .map(value -> AgentBuyerDisplayText.text(value, context))
                .toList();
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
