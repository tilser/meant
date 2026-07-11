package com.meant.api.plugin.catalog.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProductRankingModelSafetyTest {

    @Test
    void positionalModelAlwaysReceivesCanonicalKeyOrderUnderReversedInput() {
        AtomicReference<List<String>> seen = new AtomicReference<>();
        ProductRankingModel positional = model("positional-v1", candidates -> {
            seen.set(candidates.stream().map(ProductRankingModel.Candidate::canonicalProductKey).toList());
            Map<String, Integer> scores = new LinkedHashMap<>();
            for (int index = 0; index < candidates.size(); index++) {
                scores.put(candidates.get(index).canonicalProductKey(), 9_000 - index);
            }
            return scores;
        });
        ProductRankingService service = ProductRankingTestFactory.service(List.of(positional));

        List<String> first = keys(service.rank(List.of(product("b"), product("a")), RankingTestFixtures.context("shirt")));
        List<String> second = keys(service.rank(List.of(product("a"), product("b")), RankingTestFixtures.context("shirt")));

        assertThat(seen.get()).containsExactly("a", "b");
        assertThat(second).containsExactlyElementsOf(first);
    }

    @Test
    void partialExtraNullOutOfRangeInvalidVersionAndExceptionAllFallbackForWholeBatch() {
        List<ProductRankingModel> invalid = List.of(
                model("partial-v1", candidates -> Map.of("a", 5_000)),
                model("extra-v1", candidates -> Map.of("a", 5_000, "b", 5_000, "extra", 1)),
                model("null-v1", candidates -> { Map<String, Integer> values = new LinkedHashMap<>(); values.put("a", 5_000); values.put("b", null); return values; }),
                model("range-v1", candidates -> Map.of("a", 5_000, "b", 10_001)),
                model("invalid version with raw text", candidates -> Map.of("a", 5_000, "b", 5_000)),
                model("throws-v1", candidates -> { throw new IllegalStateException("sensitive payload"); })
        );

        invalid.forEach(model -> assertWholeBatchFallback(ProductRankingTestFactory.service(List.of(model))));
    }

    @Test
    void multipleConfiguredModelsFailClosedInsteadOfSelectingOne() {
        ProductRankingModel first = model("first-v1", candidates -> Map.of("a", 10_000, "b", 0));
        ProductRankingModel second = model("second-v1", candidates -> Map.of("a", 0, "b", 10_000));

        assertWholeBatchFallback(ProductRankingTestFactory.service(List.of(first, second)));
    }

    private void assertWholeBatchFallback(ProductRankingService service) {
        ProductRankingResult result = service.rank(List.of(product("b"), product("a")), RankingTestFixtures.context("shirt"));
        assertThat(result.productExplanations().values())
                .extracting(ProductRankingExplanation::execution)
                .containsOnly(ProductRankingExplanation.Execution.MODEL_FALLBACK);
        assertThat(result.products()).extracting(CanonicalProduct::key).containsExactly("a", "b");
    }

    private ProductRankingModel model(String version, Reranker reranker) {
        return new ProductRankingModel() {
            @Override public String version() { return version; }
            @Override public Map<String, Integer> rerank(List<Candidate> candidates) { return reranker.apply(candidates); }
        };
    }

    private CanonicalProduct product(String key) {
        var offer = RankingTestFixtures.offer(key.toUpperCase(), "merchant-" + key, key, "v",
                new Money(1_000, "USD"), OfferAvailabilityStatus.IN_STOCK, null, List.of());
        return RankingTestFixtures.product(key, "shirt", key.toUpperCase(), key + "-source", 5_000,
                List.of(new ProductAttribute("taxonomy", "category", "apparel")), offer);
    }

    private List<String> keys(ProductRankingResult result) {
        return result.products().stream().map(CanonicalProduct::key).toList();
    }

    @FunctionalInterface
    private interface Reranker {
        Map<String, Integer> apply(List<ProductRankingModel.Candidate> candidates);
    }
}
