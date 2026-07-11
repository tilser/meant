package com.meant.api.module.user.controller.response;

import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;

final class RankingExplanationResponseMapper {
    private RankingExplanationResponseMapper() { }

    static ProductRankingExplanationResponse product(ProductRankingExplanation explanation) {
        return explanation == null ? null : new ProductRankingExplanationResponse(
                explanation.rankingVersion(), explanation.diversityPolicyVersion(), explanation.diversityPolicyOutcome(),
                explanation.scoreBasisPoints(), explanation.finalRank(), explanation.execution(),
                explanation.diversityDecision(), explanation.deterministicTieBreakKey(),
                explanation.features().stream().map(ProductRankingFeatureResponse::from).toList());
    }

    static OfferRankingExplanationResponse offer(OfferRankingExplanation explanation) {
        return explanation == null ? null : new OfferRankingExplanationResponse(
                explanation.rankingVersion(), explanation.scoreBasisPoints(), explanation.finalRank(),
                explanation.commercialTieBreakPolicy(), explanation.deterministicTieBreakKey(),
                explanation.features().stream().map(OfferRankingFeatureResponse::from).toList());
    }
}
