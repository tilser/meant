package com.meant.api.module.review.service.dto;

import java.time.Instant;

public record ProductReview(
        String externalId,
        String author,
        Integer rating,
        String content,
        Boolean verified,
        Instant createdAt,
        String variantId,
        String variantTitle
) {
}
