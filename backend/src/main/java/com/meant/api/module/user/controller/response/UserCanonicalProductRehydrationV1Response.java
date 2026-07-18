package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserCanonicalProductsRehydrationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Fresh canonical products restored from durable identifier-only references")
public record UserCanonicalProductRehydrationV1Response(
        @Schema(
                description = "Available canonical products in deduplicated request order",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<UserGroupedProductSearchV1Response.CanonicalProductResponse> products,
        @Schema(
                description = "Unknown, unauthorized, stale, or unavailable keys in deduplicated request order",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<String> unavailableCanonicalProductKeys
) {
    public static UserCanonicalProductRehydrationV1Response from(
            UserCanonicalProductsRehydrationResult result
    ) {
        return new UserCanonicalProductRehydrationV1Response(
                result.products().stream()
                        .map(product -> UserGroupedProductSearchV1Response.CanonicalProductResponse.from(
                                product.product(),
                                product.productRankingExplanation(),
                                product.personalization(),
                                product.offerRankingExplanations(),
                                product.commercialStates()
                        ))
                        .toList(),
                result.unavailableCanonicalProductKeys()
        );
    }
}
