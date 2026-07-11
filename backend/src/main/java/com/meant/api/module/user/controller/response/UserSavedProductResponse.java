package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserSavedProductResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record UserSavedProductResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String productHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String brand,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String category,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String tone,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String productUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean remote,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int match,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double priceFrom,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int merchants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> satisfies,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> misses,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String note,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> pros,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> cons,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Review review,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Offer> offers,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String needs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> provides,
        @Schema(
                description = "False for persisted display hints; current price and availability require rehydration",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean commercialFactsAuthoritative,
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
                result.merchants(),
                result.satisfies(),
                result.misses(),
                result.note(),
                result.pros(),
                result.cons(),
                Review.from(result.review()),
                result.offers().stream().map(Offer::from).toList(),
                result.needs(),
                result.provides(),
                result.commercialFactsAuthoritative(),
                result.createdAt(),
                result.updatedAt()
        );
    }

    public record Review(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            double score,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int count,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String insight
    ) {

        private static Review from(UserSavedProductResult.Review result) {
            return new Review(result.score(), result.count(), result.insight());
        }
    }

    public record Offer(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String merchant,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            double price,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String delivery,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String merchantId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String merchantDomain,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String productVariantId,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String variantTitle,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Boolean available
    ) {

        private static Offer from(UserSavedProductResult.Offer result) {
            return new Offer(
                    result.merchant(),
                    result.price(),
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
