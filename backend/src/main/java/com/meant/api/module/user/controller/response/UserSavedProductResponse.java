package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserSavedProductResult;
import java.time.Instant;
import java.util.List;

public record UserSavedProductResponse(
        String id,
        String productHash,
        String name,
        String brand,
        String category,
        String tone,
        String imageUrl,
        String productUrl,
        boolean remote,
        int match,
        double priceFrom,
        int merchants,
        List<String> satisfies,
        List<String> misses,
        String note,
        List<String> pros,
        List<String> cons,
        Review review,
        List<Offer> offers,
        String needs,
        List<String> provides,
        Instant createdAt,
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
                result.createdAt(),
                result.updatedAt()
        );
    }

    public record Review(
            double score,
            int count,
            String insight
    ) {

        private static Review from(UserSavedProductResult.Review result) {
            return new Review(result.score(), result.count(), result.insight());
        }
    }

    public record Offer(
            String merchant,
            double price,
            String delivery,
            String merchantId,
            String merchantDomain,
            String productVariantId,
            String variantTitle,
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
