package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserSavedProductResult;
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
        return new UserSavedProductResponse(
                result.id(),
                result.productHash(),
                result.name(),
                result.brand(),
                result.category(),
                result.tone(),
                result.imageUrl(),
                result.productUrl(),
                result.remote(),
                result.match(),
                result.priceFrom(),
                result.priceFromMinorUnits(),
                result.priceCurrency(),
                result.merchants(),
                result.satisfies(),
                result.misses(),
                result.note(),
                result.pros(),
                result.cons(),
                result.review() == null ? null : SavedReview.from(result.review()),
                result.offers().stream().map(SavedOffer::from).toList(),
                result.needs(),
                result.provides(),
                result.marketCountry(),
                result.marketContextApplied(),
                result.commercialFactsAuthoritative(),
                result.details() == null ? null : UserSavedProductDetailsResponse.from(result.details()),
                result.createdAt(),
                result.updatedAt()
        );
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

        private static SavedReview from(UserSavedProductResult.Review result) {
            return new SavedReview(result.score(), result.count(), result.insight());
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
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String merchantDomain,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String productVariantId,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String variantTitle,
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Boolean available
    ) {

        private static SavedOffer from(UserSavedProductResult.Offer result) {
            return new SavedOffer(
                    result.offerKey(),
                    result.merchant(),
                    result.price(),
                    result.priceMinorUnits(),
                    result.priceCurrency(),
                    result.delivery(),
                    result.merchantId(),
                    result.merchantDomain(),
                    result.productVariantId(),
                    result.variantTitle(),
                    result.available()
            );
        }
    }
}
