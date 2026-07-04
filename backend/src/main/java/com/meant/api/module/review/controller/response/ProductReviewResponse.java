package com.meant.api.module.review.controller.response;

import com.meant.api.module.review.service.dto.ProductReview;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ProductReviewResponse", description = "Single product review returned by a merchant review provider.")
public record ProductReviewResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Provider review id.", example = "review-123")
        String externalId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Displayed review author.", example = "Ada L.")
        String author,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Review rating.", example = "5")
        Integer rating,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Review body.", example = "Great fit and fast shipping.")
        String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether the review provider marks this review as verified.")
        Boolean verified,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Review creation timestamp.")
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Reviewed product variant id.", example = "gid://shopify/ProductVariant/1")
        String variantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Reviewed product variant title.", example = "Black / Medium")
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
