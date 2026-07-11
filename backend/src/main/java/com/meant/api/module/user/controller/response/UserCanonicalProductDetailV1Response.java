package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductDetailResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Version 1 canonical product detail with selectable exact offers")
public record UserCanonicalProductDetailV1Response(
        @Schema(description = "Canonical product and all eligible independently ranked offers", requiredMode = Schema.RequiredMode.REQUIRED)
        UserGroupedProductSearchV1Response.CanonicalProductResponse product,
        @Schema(description = "Default independently ranked offer key", requiredMode = Schema.RequiredMode.REQUIRED)
        String recommendedOfferKey,
        @Schema(description = "Deterministically resolved selected offer key", requiredMode = Schema.RequiredMode.REQUIRED)
        String selectedOfferKey,
        @Schema(description = "Search-source degradation and truncation retained for this product", requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserCatalogSourceStateResponse> sourceStates
) {
    public static UserCanonicalProductDetailV1Response from(UserProductDetailResult result) {
        return new UserCanonicalProductDetailV1Response(
                UserGroupedProductSearchV1Response.CanonicalProductResponse.from(
                        result.product(),
                        result.productRankingExplanation(),
                        result.offerRankingExplanations(),
                        result.commercialStates()),
                result.recommendedOfferKey(),
                result.selectedOfferKey(),
                result.sourceStates().stream().map(UserCatalogSourceStateResponse::from).toList()
        );
    }
}
