package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.OfferRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingResult;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import com.meant.api.plugin.catalog.common.service.ProductRankingFeatureExtractor.ScoredProduct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Provider-neutral canonical-product ranking followed by independent exact-offer ranking. */
@Service
public class ProductRankingService {

    public static final String PRODUCT_RANKING_VERSION = "product-v1";
    public static final String DIVERSITY_POLICY_VERSION = "source-merchant-window-v1";

    private static final Pattern VERSION = Pattern.compile("[a-zA-Z0-9._-]{1,64}");

    private final OfferRankingService offerRankingService;
    private final ProductRankingMetrics metrics;
    private final List<ProductRankingModel> models;
    private final ProductRankingFeatureExtractor featureExtractor;

    @Autowired
    public ProductRankingService(
            OfferRankingService offerRankingService,
            ProductRankingMetrics metrics,
            List<ProductRankingModel> models,
            ProductRankingFeatureExtractor featureExtractor
    ) {
        this.offerRankingService = offerRankingService;
        this.metrics = metrics;
        this.models = models == null
                ? List.of()
                : models.stream().sorted(Comparator.comparing(model -> model.getClass().getName())).toList();
        this.featureExtractor = featureExtractor;
    }

    public ProductRankingService(
            OfferRankingService offerRankingService,
            ProductRankingMetrics metrics,
            List<ProductRankingModel> models
    ) {
        this(offerRankingService, metrics, models, new ProductRankingFeatureExtractor());
    }

    public ProductRankingService() {
        this(new OfferRankingService(), ProductRankingMetrics.noop(), List.of());
    }

    public ProductRankingResult rank(List<CanonicalProduct> products, ProductRankingContext context) {
        List<ScoredProduct> deterministic = featureExtractor.eligibleProducts(products, context).stream()
                .map(product -> featureExtractor.score(product, context))
                .toList();
        ModelApplication modelApplication = applyModel(deterministic);
        List<ScoredProduct> sorted = modelApplication.products().stream()
                .sorted(Comparator.comparingInt(ScoredProduct::scoreBasisPoints)
                        .reversed()
                        .thenComparing(entry -> entry.product().key()))
                .toList();
        List<ScoredProduct> diverse = diversify(sorted, context.diversityWindow());

        Map<String, ProductRankingExplanation> productExplanations = new LinkedHashMap<>();
        Map<String, OfferRankingExplanation> offerExplanations = new LinkedHashMap<>();
        List<CanonicalProduct> rankedProducts = new ArrayList<>();
        Map<String, Integer> originalRanks = originalRanks(sorted);
        for (int index = 0; index < diverse.size(); index++) {
            ScoredProduct entry = diverse.get(index);
            int finalRank = index + 1;
            ProductRankingExplanation explanation = entry.explanation().withFinalRank(
                    finalRank,
                    diversityDecision(finalRank, originalRanks.get(entry.product().key()))
            );
            productExplanations.put(entry.product().key(), explanation);
            metrics.recordProductFeatures(explanation.features());

            OfferRankingService.Result offers = offerRankingService.rank(entry.product().offers(), context.rankedAt());
            offers.explanations().values().forEach(value -> metrics.recordOfferFeatures(value.features()));
            offerExplanations.putAll(offers.explanations());
            rankedProducts.add(entry.product().withOffers(offers.offers()));
        }
        metrics.recordRequest(outcome(modelApplication, rankedProducts));
        return new ProductRankingResult(rankedProducts, productExplanations, offerExplanations);
    }

    private Map<String, Integer> originalRanks(List<ScoredProduct> products) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int index = 0; index < products.size(); index++) {
            ranks.put(products.get(index).product().key(), index + 1);
        }
        return ranks;
    }

    private ProductRankingExplanation.DiversityDecision diversityDecision(int finalRank, int originalRank) {
        if (finalRank < originalRank) {
            return ProductRankingExplanation.DiversityDecision.PROMOTED;
        }
        if (finalRank > originalRank) {
            return ProductRankingExplanation.DiversityDecision.DEFERRED;
        }
        return ProductRankingExplanation.DiversityDecision.NONE;
    }

    private ProductRankingMetrics.Outcome outcome(
            ModelApplication modelApplication,
            List<CanonicalProduct> products
    ) {
        if (modelApplication.execution() == ProductRankingExplanation.Execution.MODEL_FALLBACK) {
            return ProductRankingMetrics.Outcome.FALLBACK;
        }
        return products.stream().anyMatch(product -> product.retrievalSignals().isEmpty())
                ? ProductRankingMetrics.Outcome.DEGRADED
                : ProductRankingMetrics.Outcome.SUCCESS;
    }

    private ModelApplication applyModel(List<ScoredProduct> deterministic) {
        if (models.isEmpty() || deterministic.isEmpty()) {
            return new ModelApplication(deterministic, ProductRankingExplanation.Execution.DETERMINISTIC);
        }
        ProductRankingModel model = models.getFirst();
        try {
            String version = model.version();
            if (version == null || !VERSION.matcher(version).matches()) {
                throw new IllegalArgumentException("Model version is not a bounded identifier");
            }
            Map<String, Integer> scores = model.rerank(deterministic.stream()
                    .map(entry -> new ProductRankingModel.Candidate(
                            entry.product().key(),
                            entry.scoreBasisPoints(),
                            entry.explanation().features()
                    ))
                    .toList());
            Map<String, Integer> safeScores = scores == null ? Map.of() : Map.copyOf(scores);
            validateScores(safeScores);
            return new ModelApplication(
                    deterministic.stream()
                            .map(entry -> featureExtractor.withModelScore(
                                    entry,
                                    safeScores.get(entry.product().key()),
                                    version,
                                    ProductRankingExplanation.Execution.MODEL_AUGMENTED
                            ))
                            .toList(),
                    ProductRankingExplanation.Execution.MODEL_AUGMENTED
            );
        } catch (RuntimeException exception) {
            return new ModelApplication(
                    deterministic.stream()
                            .map(entry -> featureExtractor.withModelScore(
                                    entry,
                                    null,
                                    null,
                                    ProductRankingExplanation.Execution.MODEL_FALLBACK
                            ))
                            .toList(),
                    ProductRankingExplanation.Execution.MODEL_FALLBACK
            );
        }
    }

    private void validateScores(Map<String, Integer> scores) {
        scores.forEach((key, value) -> {
            if (key == null || value == null || value < 0 || value > 10_000) {
                throw new IllegalArgumentException("Model returned an invalid score");
            }
        });
    }

    private List<ScoredProduct> diversify(List<ScoredProduct> sorted, int requestedWindow) {
        int window = Math.min(requestedWindow, sorted.size());
        if (window < 2) {
            return sorted;
        }
        boolean multipleSources = sorted.stream().map(this::sourceDiversityKey).distinct().count() > 1;
        boolean multipleMerchants = sorted.stream().map(this::merchantDiversityKey).distinct().count() > 1;
        if (!multipleSources && !multipleMerchants) {
            return sorted;
        }
        int sourceCap = Math.max(1, (int) Math.ceil(window * 0.60d));
        int merchantCap = Math.max(1, (int) Math.ceil(window * 0.50d));
        Map<String, Integer> sourceCounts = new HashMap<>();
        Map<String, Integer> merchantCounts = new HashMap<>();
        List<ScoredProduct> selected = new ArrayList<>();
        Set<String> selectedKeys = new HashSet<>();
        for (ScoredProduct product : sorted) {
            if (selected.size() >= window) {
                break;
            }
            String source = sourceDiversityKey(product);
            String merchant = merchantDiversityKey(product);
            if (multipleSources && sourceCounts.getOrDefault(source, 0) >= sourceCap
                    || multipleMerchants && merchantCounts.getOrDefault(merchant, 0) >= merchantCap) {
                continue;
            }
            selected.add(product);
            selectedKeys.add(product.product().key());
            sourceCounts.merge(source, 1, Integer::sum);
            merchantCounts.merge(merchant, 1, Integer::sum);
        }
        fillWindow(sorted, window, selected, selectedKeys);
        sorted.stream()
                .filter(product -> !selectedKeys.contains(product.product().key()))
                .forEach(selected::add);
        return List.copyOf(selected);
    }

    private void fillWindow(
            List<ScoredProduct> sorted,
            int window,
            List<ScoredProduct> selected,
            Set<String> selectedKeys
    ) {
        if (selected.size() >= window) {
            return;
        }
        sorted.stream()
                .filter(product -> !selectedKeys.contains(product.product().key()))
                .limit(window - selected.size())
                .forEach(product -> {
                    selected.add(product);
                    selectedKeys.add(product.product().key());
                });
    }

    private String sourceDiversityKey(ScoredProduct entry) {
        return entry.product().provenance().stream()
                .map(ResultProvenance::discoverySource)
                .map(source -> source.provider().value() + ":" + source.type())
                .sorted()
                .findFirst()
                .orElse("unknown");
    }

    private String merchantDiversityKey(ScoredProduct entry) {
        return entry.product().offers().stream()
                .map(offer -> offer.identity().merchantScope())
                .map(scope -> scope.externalMerchantIdentity() == null
                        ? "local:" + scope.merchantIntegrationFallbackId()
                        : "external:" + scope.externalMerchantIdentity().namespace()
                        + ":" + scope.externalMerchantIdentity().value())
                .sorted()
                .findFirst()
                .orElse("unknown");
    }

    private record ModelApplication(
            List<ScoredProduct> products,
            ProductRankingExplanation.Execution execution
    ) {
    }
}
