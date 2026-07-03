package com.meant.api.module.review.service.dto;

import com.meant.api.module.review.constant.ReviewProviderType;
import java.util.List;
import java.util.UUID;

public record ProductReviewsResult(
        UUID merchantId,
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

    public static ProductReviewsResult unsupported(
            UUID merchantId,
            String productId,
            ReviewProviderType provider,
            String message
    ) {
        return new ProductReviewsResult(
                merchantId,
                productId,
                provider,
                null,
                0,
                false,
                List.of(),
                false,
                false,
                message
        );
    }

    public ProductReviewsResult withCached(boolean cached) {
        return new ProductReviewsResult(
                merchantId,
                productId,
                provider,
                rating,
                reviewCount,
                hasMore,
                reviews,
                cached,
                supported,
                message
        );
    }
}
