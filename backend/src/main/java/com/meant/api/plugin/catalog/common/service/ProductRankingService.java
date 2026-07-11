package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.OfferRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingResult;
import com.meant.api.plugin.catalog.common.service.ProductRankingFeatureExtractor.ScoredProduct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Orchestrates hard eligibility, product scoring, safe model execution, diversity, and offer ranking. */
@Service
@RequiredArgsConstructor
public class ProductRankingService {

    public static final String PRODUCT_RANKING_VERSION = "product-v2";
    private final ProductHardEligibilityPolicy eligibilityPolicy;
    private final ProductRankingFeatureExtractor featureExtractor;
    private final ProductRankingModelExecutor modelExecutor;
    private final ProductDiversityPolicy diversityPolicy;
    private final OfferRankingService offerRankingService;
    private final ProductRankingMetrics metrics;

    public ProductRankingResult rank(List<CanonicalProduct> products, ProductRankingContext context) {
        List<ScoredProduct> deterministic = eligibilityPolicy.eligible(products, context).stream()
                .map(product -> featureExtractor.score(product, context)).toList();
        ProductRankingModelExecutor.Result model = modelExecutor.execute(deterministic);
        List<ScoredProduct> sorted = model.products().stream()
                .sorted(Comparator.comparingInt(ScoredProduct::scoreBasisPoints).reversed()
                        .thenComparing(entry -> entry.product().key())).toList();
        ProductDiversityPolicy.Result diversity = diversityPolicy.apply(sorted, context.diversityWindow());
        return assemble(sorted, diversity, model.execution(), context);
    }

    private ProductRankingResult assemble(
            List<ScoredProduct> relevanceOrder,
            ProductDiversityPolicy.Result diversity,
            ProductRankingExplanation.Execution execution,
            ProductRankingContext context
    ) {
        Map<String, Integer> originalRanks = new HashMap<>();
        for (int index = 0; index < relevanceOrder.size(); index++) originalRanks.put(relevanceOrder.get(index).product().key(), index + 1);
        Map<String, ProductRankingExplanation> productExplanations = new LinkedHashMap<>();
        Map<String, OfferRankingExplanation> offerExplanations = new LinkedHashMap<>();
        List<CanonicalProduct> ranked = new ArrayList<>();
        for (int index = 0; index < diversity.products().size(); index++) {
            ScoredProduct entry = diversity.products().get(index);
            int finalRank = index + 1;
            ProductRankingExplanation explanation = entry.explanation().withFinalRank(
                    finalRank, diversity.outcome(), decision(finalRank, originalRanks.get(entry.product().key())));
            productExplanations.put(entry.product().key(), explanation);
            metrics.recordProductFeatures(explanation.features());
            OfferRankingService.Result offers = offerRankingService.rank(entry.product().offers(), context);
            offers.explanations().values().forEach(value -> metrics.recordOfferFeatures(value.features()));
            offerExplanations.putAll(offers.explanations());
            ranked.add(entry.product().withOffers(offers.offers()));
        }
        metrics.recordRequest(outcome(execution, ranked));
        return new ProductRankingResult(ranked, productExplanations, offerExplanations);
    }

    private ProductRankingExplanation.DiversityDecision decision(int finalRank, int originalRank) {
        if (finalRank < originalRank) return ProductRankingExplanation.DiversityDecision.PROMOTED;
        if (finalRank > originalRank) return ProductRankingExplanation.DiversityDecision.DEFERRED;
        return ProductRankingExplanation.DiversityDecision.NONE;
    }

    private ProductRankingMetrics.Outcome outcome(ProductRankingExplanation.Execution execution, List<CanonicalProduct> products) {
        if (execution == ProductRankingExplanation.Execution.MODEL_FALLBACK) return ProductRankingMetrics.Outcome.FALLBACK;
        return products.stream().anyMatch(product -> product.retrievalSignals().isEmpty())
                ? ProductRankingMetrics.Outcome.DEGRADED : ProductRankingMetrics.Outcome.SUCCESS;
    }
}
