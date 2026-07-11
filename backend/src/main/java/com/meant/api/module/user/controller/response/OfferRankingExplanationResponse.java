package com.meant.api.module.user.controller.response;

import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Reproducible offer-ranking trace with unknown facts preserved")
public record OfferRankingExplanationResponse(
        @Schema(description = "Deterministic offer-ranking version", requiredMode = Schema.RequiredMode.REQUIRED)
        String rankingVersion,
        @Schema(description = "Final offer score in basis points", requiredMode = Schema.RequiredMode.REQUIRED)
        int scoreBasisPoints,
        @Schema(description = "Final one-based rank inside the canonical product", requiredMode = Schema.RequiredMode.REQUIRED)
        int finalRank,
        @Schema(description = "Disclosed commercial tie-break policy", requiredMode = Schema.RequiredMode.REQUIRED)
        OfferRankingExplanation.CommercialTieBreakPolicy commercialTieBreakPolicy,
        @Schema(description = "Stable offer key used only after offer-feature ties", requiredMode = Schema.RequiredMode.REQUIRED)
        String deterministicTieBreakKey,
        @Schema(description = "Typed offer features; unavailable facts have no numeric value", requiredMode = Schema.RequiredMode.REQUIRED)
        List<OfferRankingFeatureResponse> features
) {
    static OfferRankingExplanationResponse from(OfferRankingExplanation explanation) {
        return RankingExplanationResponseMapper.offer(explanation);
    }
}
