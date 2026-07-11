package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import com.meant.api.plugin.catalog.common.service.ProductRankingFeatureExtractor.ScoredProduct;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Executes exactly one optional whole-batch model with canonical input and strict output validation. */
@Component
public class ProductRankingModelExecutor {

    private static final Pattern VERSION = Pattern.compile("[a-zA-Z0-9._-]{1,64}");
    private final List<ProductRankingModel> models;
    private final ProductRankingFeatureExtractor featureExtractor;

    public ProductRankingModelExecutor(List<ProductRankingModel> models, ProductRankingFeatureExtractor featureExtractor) {
        this.models = models == null ? List.of() : List.copyOf(models);
        this.featureExtractor = featureExtractor;
    }

    Result execute(List<ScoredProduct> products) {
        if (products.isEmpty() || models.isEmpty()) {
            return new Result(products, ProductRankingExplanation.Execution.DETERMINISTIC);
        }
        if (models.size() != 1) {
            return fallback(products);
        }
        ProductRankingModel model = models.getFirst();
        try {
            String version = model.version();
            if (version == null || !VERSION.matcher(version).matches()) {
                throw new IllegalArgumentException("Model version is not a bounded identifier");
            }
            List<ScoredProduct> canonical = products.stream()
                    .sorted(Comparator.comparing(entry -> entry.product().key()))
                    .toList();
            List<ProductRankingModel.Candidate> candidates = canonical.stream()
                    .map(entry -> new ProductRankingModel.Candidate(
                            entry.product().key(), entry.scoreBasisPoints(), entry.explanation().features()))
                    .toList();
            Map<String, Integer> scores = model.rerank(candidates);
            validate(candidates, scores);
            return new Result(canonical.stream()
                    .map(entry -> featureExtractor.withModel(
                            entry, scores.get(entry.product().key()), version,
                            ProductRankingExplanation.Execution.MODEL_AUGMENTED))
                    .toList(), ProductRankingExplanation.Execution.MODEL_AUGMENTED);
        } catch (RuntimeException exception) {
            return fallback(products);
        }
    }

    private void validate(List<ProductRankingModel.Candidate> candidates, Map<String, Integer> scores) {
        if (scores == null) {
            throw new IllegalArgumentException("Model score map is required");
        }
        Set<String> expected = candidates.stream()
                .map(ProductRankingModel.Candidate::canonicalProductKey)
                .collect(Collectors.toUnmodifiableSet());
        if (!scores.keySet().equals(expected)) {
            throw new IllegalArgumentException("Model score keys must exactly match candidate keys");
        }
        scores.forEach((key, value) -> {
            if (key == null || value == null || value < 0 || value > 10_000) {
                throw new IllegalArgumentException("Model returned an invalid score");
            }
        });
    }

    private Result fallback(List<ScoredProduct> products) {
        return new Result(products.stream()
                .map(entry -> featureExtractor.withModel(
                        entry, null, null, ProductRankingExplanation.Execution.MODEL_FALLBACK))
                .toList(), ProductRankingExplanation.Execution.MODEL_FALLBACK);
    }

    record Result(List<ScoredProduct> products, ProductRankingExplanation.Execution execution) { }
}
