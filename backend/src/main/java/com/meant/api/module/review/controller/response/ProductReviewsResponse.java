package com.meant.api.module.review.controller.response;

import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import java.util.List;
import java.util.UUID;

public record ProductReviewsResponse(
        UUID merchantId,
        String productId,
        ReviewProviderType provider,
        Double rating,
        Integer reviewCount,
        boolean hasMore,
        List<ProductReviewResponse> reviews,
        boolean cached,
        boolean supported,
        String message
) {

    public static ProductReviewsResponse from(ProductReviewsResult result) {
        return new ProductReviewsResponse(
                result.merchantId(),
                result.productId(),
                result.provider(),
                result.rating(),
                result.reviewCount(),
                result.hasMore(),
                result.reviews().stream()
                        .map(ProductReviewResponse::from)
                        .toList(),
                result.cached(),
                result.supported(),
                result.message()
        );
    }
}
