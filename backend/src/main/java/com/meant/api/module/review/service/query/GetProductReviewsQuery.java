package com.meant.api.module.review.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record GetProductReviewsQuery(
        @NotNull
        UUID merchantId,

        @NotBlank
        String productId,

        @Positive
        Integer limit,

        @PositiveOrZero
        Integer offset
) {
}
