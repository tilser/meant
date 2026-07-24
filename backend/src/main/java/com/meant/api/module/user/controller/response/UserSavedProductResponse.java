package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import com.meant.api.module.merchant.service.MerchantProductMessageSanitizer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record UserSavedProductResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String productHash,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String name,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String brand,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String category,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String tone,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String imageUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String productUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean remote,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer match,
        @Schema(
                description = "Current rehydrated price, or null when unavailable",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        Double priceFrom,
        @Schema(
                description = "Current rehydrated price in ISO currency minor units, or null when unavailable",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        Long priceFromMinorUnits,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String priceCurrency,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer merchants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> satisfies,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> misses,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String note,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> pros,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> cons,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        SavedReview review,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SavedOffer> offers,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String needs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> provides,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String marketCountry,
        @Schema(
                description = "True when the provider lookup used the returned ISO market country",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean marketContextApplied,
        @Schema(
                description = "True only when response facts came from current provider rehydration",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean commercialFactsAuthoritative,
        @Schema(
                description = "Full current provider detail returned only by the saved-product detail endpoint",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        UserSavedProductDetailsResponse details,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {

    public static UserSavedProductResponse from(UserSavedProductResult result) {
        MerchantProductMessageSanitizer.TransportContext buyerContext = buyerContext(result);
        return new UserSavedProductResponse(
                result.id(),
                result.productHash(),
                buyerText(result.name(), buyerContext),
                buyerText(result.brand(), buyerContext),
                buyerText(result.category(), buyerContext),
                result.tone(),
                buyerUrl(result.imageUrl(), buyerContext),
                buyerUrl(result.productUrl(), buyerContext),
                result.remote(),
                result.match(),
                result.priceFrom(),
                result.priceFromMinorUnits(),
                result.priceCurrency(),
                result.merchants(),
                buyerTextValues(result.satisfies(), buyerContext),
                buyerTextValues(result.misses(), buyerContext),
                buyerText(result.note(), buyerContext),
                buyerTextValues(result.pros(), buyerContext),
                buyerTextValues(result.cons(), buyerContext),
                result.review() == null ? null : SavedReview.from(result.review(), buyerContext),
                result.offers().stream()
                        .map(offer -> SavedOffer.from(offer, buyerContext))
                        .toList(),
                buyerText(result.needs(), buyerContext),
                buyerTextValues(result.provides(), buyerContext),
                result.marketCountry(),
                result.marketContextApplied(),
                result.commercialFactsAuthoritative(),
                result.details() == null ? null : UserSavedProductDetailsResponse.from(result.details()),
                result.createdAt(),
                result.updatedAt()
        );
    }

    private static MerchantProductMessageSanitizer.TransportContext buyerContext(
            UserSavedProductResult result
    ) {
        String merchantOrigin = result.offers().stream()
                .map(UserSavedProductResult.Offer::merchantOrigin)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> result.details() != null && result.details().merchantOrigin() != null
                        ? result.details().merchantOrigin()
                        : result.merchantOrigin());
        List<String> technicalAliases = java.util.stream.Stream.concat(
                        result.technicalEndpointAliases().stream(),
                        result.details() == null
                                ? java.util.stream.Stream.empty()
                                : result.details().technicalEndpointAliases().stream()
                )
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        return MerchantProductMessageSanitizer.context(
                merchantOrigin,
                null,
                technicalAliases.toArray(String[]::new)
        );
    }

    private static String buyerText(
            String value,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        return MerchantProductMessageSanitizer.sanitizeBuyerText(value, context);
    }

    private static List<String> buyerTextValues(
            List<String> values,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        return values.stream().map(value -> buyerText(value, context)).toList();
    }

    private static String buyerUrl(
            String value,
            MerchantProductMessageSanitizer.TransportContext context
    ) {
        return MerchantProductMessageSanitizer.buyerSafeUrl(value, context);
    }

    @Schema(name = "UserSavedProductReview")
    public record SavedReview(
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Double score,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer count,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String insight
    ) {

        private static SavedReview from(
                UserSavedProductResult.Review result,
                MerchantProductMessageSanitizer.TransportContext context
        ) {
            return new SavedReview(
                    result.score(),
                    result.count(),
                    buyerText(result.insight(), context)
            );
        }
    }

    @Schema(name = "UserSavedProductOffer")
    public record SavedOffer(
            @Schema(
                    description = "Server-issued key for selecting this freshly rehydrated saved offer",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            String offerKey,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String merchant,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Double price,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Long priceMinorUnits,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String priceCurrency,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String delivery,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String merchantId,
            @Schema(
                    description = "Verified official storefront origin for buyer display",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            String merchantOrigin,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String productVariantId,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String variantTitle,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Boolean available
    ) {

        private static SavedOffer from(
                UserSavedProductResult.Offer result,
                MerchantProductMessageSanitizer.TransportContext parentContext
        ) {
            String merchantOrigin =
                    MerchantBuyerTextSanitizer.buyerSafeMerchantOrigin(result.merchantOrigin());
            MerchantProductMessageSanitizer.TransportContext context =
                    MerchantProductMessageSanitizer.context(
                            merchantOrigin,
                            null,
                            parentContext.endpoints().toArray(String[]::new)
                    );
            return new SavedOffer(
                    result.offerKey(),
                    buyerText(result.merchant(), context),
                    result.price(),
                    result.priceMinorUnits(),
                    result.priceCurrency(),
                    buyerText(result.delivery(), context),
                    result.merchantId(),
                    merchantOrigin,
                    result.productVariantId(),
                    buyerText(result.variantTitle(), context),
                    result.available()
            );
        }
    }
}
