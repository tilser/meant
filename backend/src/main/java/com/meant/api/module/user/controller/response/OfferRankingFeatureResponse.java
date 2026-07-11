package com.meant.api.module.user.controller.response;

import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One named offer-ranking feature")
public record OfferRankingFeatureResponse(
        @Schema(description = "Controlled offer feature name", requiredMode = Schema.RequiredMode.REQUIRED)
        OfferRankingExplanation.Name name,
        @Schema(description = "Whether this fact was known", requiredMode = Schema.RequiredMode.REQUIRED)
        OfferRankingExplanation.Availability availability,
        @Schema(description = "Normalized feature value in basis points", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer valueBasisPoints,
        @Schema(description = "Versioned policy weight", requiredMode = Schema.RequiredMode.REQUIRED)
        int weight
) {
    static OfferRankingFeatureResponse from(OfferRankingExplanation.Feature feature) {
        return new OfferRankingFeatureResponse(feature.name(), feature.availability(), feature.valueBasisPoints(), feature.weight());
    }
}
