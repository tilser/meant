package com.meant.api.module.review.controller.response;

import com.meant.api.module.review.service.dto.ProductReview;
import java.time.Instant;

public record ProductReviewResponse(
        String externalId,
        String author,
        Integer rating,
        String content,
        Boolean verified,
        Instant createdAt,
        String variantId,
        String variantTitle
) {

    public static ProductReviewResponse from(ProductReview review) {
        return new ProductReviewResponse(
                review.externalId(),
                review.author(),
                review.rating(),
                review.content(),
                review.verified(),
                review.createdAt(),
                review.variantId(),
                review.variantTitle()
        );
    }
}
