package com.meant.api.module.user.controller.response;

import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Reproducible product-ranking trace without prompts or personal raw text")
public record ProductRankingExplanationResponse(
        @Schema(description = "Deterministic product-ranking version", requiredMode = Schema.RequiredMode.REQUIRED)
        String rankingVersion,
        @Schema(description = "Bounded source and merchant diversity policy version", requiredMode = Schema.RequiredMode.REQUIRED)
        String diversityPolicyVersion,
        @Schema(description = "Whether diversity caps were strict or relaxed because no full feasible window existed", requiredMode = Schema.RequiredMode.REQUIRED)
        ProductRankingExplanation.DiversityPolicyOutcome diversityPolicyOutcome,
        @Schema(description = "Final product relevance score in basis points", requiredMode = Schema.RequiredMode.REQUIRED)
        int scoreBasisPoints,
        @Schema(description = "Final one-based rank after diversity control", requiredMode = Schema.RequiredMode.REQUIRED)
        int finalRank,
        @Schema(description = "Deterministic, model-augmented, or safe fallback execution", requiredMode = Schema.RequiredMode.REQUIRED)
        ProductRankingExplanation.Execution execution,
        @Schema(description = "Whether diversity control promoted or deferred this product", requiredMode = Schema.RequiredMode.REQUIRED)
        ProductRankingExplanation.DiversityDecision diversityDecision,
        @Schema(description = "Stable canonical key used only after relevance ties", requiredMode = Schema.RequiredMode.REQUIRED)
        String deterministicTieBreakKey,
        @Schema(description = "Typed product feature values and calibration versions", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductRankingFeatureResponse> features
) {
    static ProductRankingExplanationResponse from(ProductRankingExplanation explanation) {
        return RankingExplanationResponseMapper.product(explanation);
    }
}
