package com.meant.api.module.catalog.service;

import java.util.List;

public final class ProductRankingTestFactory {

    private ProductRankingTestFactory() { }

    public static ProductRankingService service() {
        return service(List.of(), ProductRankingMetrics.noop());
    }

    public static ProductRankingService service(List<ProductRankingModel> models) {
        return service(models, ProductRankingMetrics.noop());
    }

    public static ProductRankingService service(List<ProductRankingModel> models, ProductRankingMetrics metrics) {
        return service(models, metrics, ProductHardEligibilityMetrics.noop());
    }

    public static ProductRankingService service(
            List<ProductRankingModel> models,
            ProductRankingMetrics metrics,
            ProductHardEligibilityMetrics eligibilityMetrics
    ) {
        RankingScorePolicy scorePolicy = new RankingScorePolicy();
        RankingFreshnessScorer freshnessScorer = new RankingFreshnessScorer();
        ProductRankingFeatureExtractor productExtractor = new ProductRankingFeatureExtractor(scorePolicy, freshnessScorer);
        OfferRankingFeatureExtractor offerExtractor = new OfferRankingFeatureExtractor(
                new OfferDeliveryChoicePolicy(), freshnessScorer, scorePolicy);
        return new ProductRankingService(
                new ProductHardEligibilityPolicy(eligibilityMetrics),
                productExtractor,
                new ProductRankingModelExecutor(models, productExtractor),
                new ProductDiversityPolicy(new DiversityFeasibility()),
                new OfferRankingService(offerExtractor),
                metrics
        );
    }
}
