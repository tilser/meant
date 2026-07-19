package com.meant.api.module.agent.service.dto;

import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.service.dto.ProductReview;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import java.util.List;

/** Agent review artifact that can also represent an offer without a local review integration. */
public record AgentProductReviewsResult(
        String merchantId,
        String productId,
        ReviewProviderType provider,
        Double rating,
        Integer reviewCount,
        boolean hasMore,
        List<ProductReview> reviews,
        boolean cached,
        boolean supported,
        String message
) {

    public AgentProductReviewsResult {
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
    }

    public static AgentProductReviewsResult from(ProductReviewsResult result) {
        return new AgentProductReviewsResult(
                result.merchantId().toString(),
                result.productId(),
                result.provider(),
                result.rating(),
                result.reviewCount(),
                result.hasMore(),
                result.reviews(),
                result.cached(),
                result.supported(),
                result.message()
        );
    }

    public static AgentProductReviewsResult unavailable(String productId, String message) {
        return new AgentProductReviewsResult(
                null,
                productId,
                ReviewProviderType.UNKNOWN,
                null,
                0,
                false,
                List.of(),
                false,
                false,
                message
        );
    }
}
