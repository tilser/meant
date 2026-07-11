package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRetrievalSignal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Extracts provider-neutral product relevance features after hard eligibility. */
@Component
@RequiredArgsConstructor
public class ProductRankingFeatureExtractor {

    static final int MODEL_WEIGHT = 20;
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "and", "for", "in", "of", "on", "or", "the", "to", "with");
    private static final List<String> COMMERCIAL_MARKERS = List.of(
            "affiliate", "commission", "commercial", "economics", "sponsored"
    );
    private final RankingScorePolicy scorePolicy;
    private final RankingFreshnessScorer freshnessScorer;

    ScoredProduct score(CanonicalProduct product, ProductRankingContext context) {
        ProductText text = ProductText.from(product);
        List<ProductRankingExplanation.Feature> features = new ArrayList<>();
        features.add(sourceIntentFit(product));
        add(features, ProductRankingExplanation.Name.LEXICAL_INTENT_FIT, lexicalFit(text.searchable(), context.normalizedIntent()), 30);
        add(features, ProductRankingExplanation.Name.DURABLE_PREFERENCE_FIT, preferenceFit(text, context.preferences()), 15);
        add(features, ProductRankingExplanation.Name.INVENTORY_RELATIONSHIP, inventoryFit(context.inventoryRelationships().get(product.key())), 10);
        add(features, ProductRankingExplanation.Name.QUALITY_EVIDENCE, quality(product), 6);
        add(features, ProductRankingExplanation.Name.IDENTITY_CONFIDENCE,
                product.identityEvidence().stream().mapToInt(value -> value.confidenceBasisPoints()).max().stream().boxed().findFirst().orElse(null), 5);
        add(features, ProductRankingExplanation.Name.FRESHNESS, freshnessScorer.score(product.provenance(), context.rankedAt()), 4);
        features.add(ProductRankingExplanation.Feature.unknown(ProductRankingExplanation.Name.MODEL_RERANK, 0));
        return scored(product, features, ProductRankingExplanation.Execution.DETERMINISTIC);
    }

    ScoredProduct withModel(ScoredProduct entry, Integer value, String version, ProductRankingExplanation.Execution execution) {
        List<ProductRankingExplanation.Feature> features = entry.explanation().features().stream()
                .filter(feature -> feature.name() != ProductRankingExplanation.Name.MODEL_RERANK)
                .collect(Collectors.toCollection(ArrayList::new));
        features.add(value == null
                ? ProductRankingExplanation.Feature.unknown(ProductRankingExplanation.Name.MODEL_RERANK, 0)
                : ProductRankingExplanation.Feature.available(ProductRankingExplanation.Name.MODEL_RERANK, value, MODEL_WEIGHT, List.of(version)));
        return scored(entry.product(), features, execution);
    }

    private ScoredProduct scored(CanonicalProduct product, List<ProductRankingExplanation.Feature> features, ProductRankingExplanation.Execution execution) {
        int score = scorePolicy.productScore(features);
        return new ScoredProduct(product, score, new ProductRankingExplanation(
                ProductRankingService.PRODUCT_RANKING_VERSION, ProductDiversityPolicy.VERSION, score, 1, execution,
                ProductRankingExplanation.DiversityPolicyOutcome.STRICT,
                ProductRankingExplanation.DiversityDecision.NONE, product.key(), features));
    }

    private ProductRankingExplanation.Feature sourceIntentFit(CanonicalProduct product) {
        List<ProductRetrievalSignal> signals = product.retrievalSignals().stream()
                .filter(signal -> signal.feature() == ProductRetrievalSignal.Feature.INTENT_FIT).toList();
        if (signals.isEmpty()) {
            return ProductRankingExplanation.Feature.unknown(ProductRankingExplanation.Name.CALIBRATED_SOURCE_INTENT_FIT, 30);
        }
        return ProductRankingExplanation.Feature.available(
                ProductRankingExplanation.Name.CALIBRATED_SOURCE_INTENT_FIT,
                signals.stream().mapToInt(ProductRetrievalSignal::valueBasisPoints).max().orElseThrow(), 30,
                signals.stream().map(ProductRetrievalSignal::calibrationVersion).distinct().sorted().toList());
    }

    private Integer lexicalFit(String text, String intent) {
        Set<String> expected = tokens(intent);
        if (expected.isEmpty()) return null;
        Set<String> actual = tokens(text);
        return (int) Math.round(expected.stream().filter(actual::contains).count() * 10_000.0d / expected.size());
    }

    private Integer preferenceFit(ProductText text, List<ProductRankingContext.PreferenceSignal> signals) {
        if (signals == null || signals.isEmpty()) return null;
        long total = signals.stream().mapToLong(signal -> Math.abs(signal.weightBasisPoints())).sum();
        if (total == 0) return null;
        long contribution = signals.stream().filter(signal -> text.matches(signal)).mapToLong(ProductRankingContext.PreferenceSignal::weightBasisPoints).sum();
        return Math.max(0, Math.min(10_000, (int) Math.round(5_000.0d + contribution * 5_000.0d / total)));
    }

    private Integer inventoryFit(ProductRankingContext.InventoryRelationship value) {
        return value == null ? null : switch (value) { case NONE -> 5_000; case DUPLICATE -> 1_000; case COMPLEMENT -> 8_000; case RESTOCK -> 10_000; };
    }

    private int quality(CanonicalProduct product) {
        int known = (product.title() == null ? 0 : 1) + (product.description() == null ? 0 : 1)
                + (product.media().isEmpty() ? 0 : 1)
                + (product.attributes().stream().anyMatch(attribute -> !commercial(attribute)) ? 1 : 0)
                + (product.materials().isEmpty() ? 0 : 1) + (product.certifications().isEmpty() ? 0 : 1);
        return (int) Math.round(known * 10_000.0d / 6.0d);
    }

    private void add(List<ProductRankingExplanation.Feature> features, ProductRankingExplanation.Name name, Integer value, int weight) {
        features.add(value == null ? ProductRankingExplanation.Feature.unknown(name, weight)
                : ProductRankingExplanation.Feature.available(name, value, weight, List.of()));
    }

    private static boolean commercial(ProductAttribute attribute) {
        String text = Stream.of(attribute.group(), attribute.name(), attribute.value()).filter(Objects::nonNull)
                .map(ProductRankingFeatureExtractor::normalized).filter(Objects::nonNull).collect(Collectors.joining(" "));
        return COMMERCIAL_MARKERS.stream().anyMatch(text::contains);
    }

    private static Set<String> tokens(String value) {
        String normalized = normalized(value);
        return normalized == null ? Set.of() : Stream.of(TOKEN_SPLIT.split(normalized))
                .filter(token -> token.length() > 1 && !STOP_WORDS.contains(token)).collect(Collectors.toSet());
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    }

    private record ProductText(String searchable, String brand, String category, String materials, String certifications) {
        static ProductText from(CanonicalProduct product) {
            String attributes = product.attributes().stream().filter(attribute -> !commercial(attribute))
                    .flatMap(attribute -> Stream.of(attribute.group(), attribute.name(), attribute.value())).filter(Objects::nonNull).collect(Collectors.joining(" "));
            String materials = product.materials().stream().map(value -> value.name()).collect(Collectors.joining(" "));
            String certifications = product.certifications().stream().flatMap(value -> Stream.of(value.name(), value.issuer())).filter(Objects::nonNull).collect(Collectors.joining(" "));
            String brand = attributes(product, "brand", "manufacturer");
            String category = attributes(product, "category");
            String searchable = Stream.of(product.title(), product.description(), attributes, materials, certifications, brand, category).filter(Objects::nonNull).collect(Collectors.joining(" "));
            return new ProductText(normalizedOrEmpty(searchable), normalizedOrEmpty(brand), normalizedOrEmpty(category), normalizedOrEmpty(materials), normalizedOrEmpty(certifications));
        }
        boolean matches(ProductRankingContext.PreferenceSignal signal) {
            return switch (signal.type()) { case BRAND -> tokenMatch(brand, signal.normalizedValue()); case CATEGORY -> tokenMatch(category, signal.normalizedValue());
                case MATERIAL -> tokenMatch(materials, signal.normalizedValue()); case CERTIFICATION -> tokenMatch(certifications, signal.normalizedValue());
                case FILTER, QUERY -> tokenMatch(searchable, signal.normalizedValue()); };
        }
        private static boolean tokenMatch(String facts, String expected) {
            Set<String> expectedTokens = tokens(expected);
            return !expectedTokens.isEmpty() && tokens(facts).containsAll(expectedTokens);
        }
        private static String attributes(CanonicalProduct product, String... names) {
            Set<String> expected = Set.of(names);
            return product.attributes().stream().filter(attribute -> expected.stream().anyMatch(name -> name.equals(normalized(attribute.name()))))
                    .map(ProductAttribute::value).collect(Collectors.joining(" "));
        }
        private static String normalizedOrEmpty(String value) { return Objects.requireNonNullElse(normalized(value), ""); }
    }

    record ScoredProduct(CanonicalProduct product, int scoreBasisPoints, ProductRankingExplanation explanation) { }
}
