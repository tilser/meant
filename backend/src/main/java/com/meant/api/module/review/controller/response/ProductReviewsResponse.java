package com.meant.api.module.review.controller.response;

import com.meant.api.module.review.constant.ReviewProviderType;
import com.meant.api.module.review.service.dto.ProductReviewsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "ProductReviewsResponse", description = "Product reviews and aggregate rating from a merchant review provider.")
public record ProductReviewsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Local merchant UUID.", example = "00000000-0000-0000-0000-000000000001")
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Remote product id.", example = "gid://shopify/Product/1")
        String productId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Review provider used for this product.")
        ReviewProviderType provider,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Aggregate product rating.", example = "4.7")
        Double rating,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Total provider review count.", example = "128")
        Integer reviewCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether more reviews are available after this page.")
        boolean hasMore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Reviews returned for the requested page.")
        List<ProductReviewResponse> reviews,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether this response was served from cache.")
        boolean cached,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether the merchant has a supported review provider.")
        boolean supported,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "User-safe status message.", example = "Reviews are temporarily unavailable.")
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
