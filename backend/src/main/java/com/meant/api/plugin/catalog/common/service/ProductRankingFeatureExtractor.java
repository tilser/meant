package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchPriceFilter;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext.PreferenceSignal;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRetrievalSignal;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Extracts only provider-neutral relevance features and enforces trustworthy hard facts. */
@Component
public class ProductRankingFeatureExtractor {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "for", "in", "of", "on", "or", "the", "to", "with"
    );
    private static final int SOURCE_WEIGHT = 30;
    private static final int LEXICAL_WEIGHT = 30;
    private static final int PREFERENCE_WEIGHT = 15;
    private static final int INVENTORY_WEIGHT = 10;
    private static final int QUALITY_WEIGHT = 6;
    private static final int CONFIDENCE_WEIGHT = 5;
    private static final int FRESHNESS_WEIGHT = 4;
    static final int MODEL_WEIGHT = 20;

    List<CanonicalProduct> eligibleProducts(
            List<CanonicalProduct> products,
            ProductRankingContext context
    ) {
        return products == null
                ? List.of()
                : products.stream().filter(Objects::nonNull).filter(product -> eligible(product, context)).toList();
    }

    ScoredProduct score(CanonicalProduct product, ProductRankingContext context) {
        ProductText text = ProductText.from(product);
        List<ProductRankingExplanation.Feature> features = new ArrayList<>();
        features.add(sourceIntentFit(product));
        add(features, ProductRankingExplanation.Name.LEXICAL_INTENT_FIT,
                lexicalIntentFit(text.searchable(), context.normalizedIntent()), LEXICAL_WEIGHT);
        add(features, ProductRankingExplanation.Name.DURABLE_PREFERENCE_FIT,
                preferenceFit(text, context.preferences()), PREFERENCE_WEIGHT);
        add(features, ProductRankingExplanation.Name.INVENTORY_RELATIONSHIP,
                inventoryFit(context.inventoryRelationships().get(product.key())), INVENTORY_WEIGHT);
        add(features, ProductRankingExplanation.Name.QUALITY_EVIDENCE, quality(product), QUALITY_WEIGHT);
        add(features, ProductRankingExplanation.Name.IDENTITY_CONFIDENCE,
                identityConfidence(product), CONFIDENCE_WEIGHT);
        add(features, ProductRankingExplanation.Name.FRESHNESS,
                freshness(product, context.rankedAt()), FRESHNESS_WEIGHT);
        features.add(ProductRankingExplanation.Feature.unknown(
                ProductRankingExplanation.Name.MODEL_RERANK,
                MODEL_WEIGHT
        ));
        return scored(product, features, ProductRankingExplanation.Execution.DETERMINISTIC);
    }

    ScoredProduct withModelScore(
            ScoredProduct entry,
            Integer modelScore,
            String modelVersion,
            ProductRankingExplanation.Execution execution
    ) {
        List<ProductRankingExplanation.Feature> features = entry.explanation().features().stream()
                .filter(feature -> feature.name() != ProductRankingExplanation.Name.MODEL_RERANK)
                .collect(Collectors.toCollection(ArrayList::new));
        features.add(modelScore == null
                ? ProductRankingExplanation.Feature.unknown(ProductRankingExplanation.Name.MODEL_RERANK, MODEL_WEIGHT)
                : ProductRankingExplanation.Feature.available(
                        ProductRankingExplanation.Name.MODEL_RERANK,
                        modelScore,
                        MODEL_WEIGHT,
                        List.of(modelVersion)
                ));
        return scored(entry.product(), features, execution);
    }

    private ScoredProduct scored(
            CanonicalProduct product,
            List<ProductRankingExplanation.Feature> features,
            ProductRankingExplanation.Execution execution
    ) {
        int score = weightedScore(features);
        return new ScoredProduct(product, score, new ProductRankingExplanation(
                ProductRankingService.PRODUCT_RANKING_VERSION,
                ProductRankingService.DIVERSITY_POLICY_VERSION,
                score,
                1,
                execution,
                ProductRankingExplanation.DiversityDecision.NONE,
                product.key(),
                features
        ));
    }

    private ProductRankingExplanation.Feature sourceIntentFit(CanonicalProduct product) {
        List<ProductRetrievalSignal> signals = product.retrievalSignals().stream()
                .filter(signal -> signal.feature() == ProductRetrievalSignal.Feature.INTENT_FIT)
                .toList();
        if (signals.isEmpty()) {
            return ProductRankingExplanation.Feature.unknown(
                    ProductRankingExplanation.Name.CALIBRATED_SOURCE_INTENT_FIT,
                    SOURCE_WEIGHT
            );
        }
        return ProductRankingExplanation.Feature.available(
                ProductRankingExplanation.Name.CALIBRATED_SOURCE_INTENT_FIT,
                signals.stream().mapToInt(ProductRetrievalSignal::valueBasisPoints).max().orElseThrow(),
                SOURCE_WEIGHT,
                signals.stream()
                        .map(ProductRetrievalSignal::calibrationVersion)
                        .distinct()
                        .sorted()
                        .toList()
        );
    }

    private boolean eligible(CanonicalProduct product, ProductRankingContext context) {
        return availabilityEligible(product)
                && priceEligible(product, context)
                && categoryEligible(product, context.hardFilters());
    }

    private boolean availabilityEligible(CanonicalProduct product) {
        return product.offers().stream().anyMatch(offer -> switch (offer.availability().status()) {
            case IN_STOCK, PREORDER, BACKORDER, UNKNOWN -> true;
            case OUT_OF_STOCK, DISCONTINUED -> false;
        });
    }

    private boolean priceEligible(CanonicalProduct product, ProductRankingContext context) {
        CatalogSearchPriceFilter filter = context.hardFilters() == null ? null : context.hardFilters().price();
        String currency = context.searchContext() == null ? null : context.searchContext().currency();
        if (filter == null || currency == null || currency.isBlank()) {
            return true;
        }
        boolean hasUnknown = product.offers().stream()
                .anyMatch(offer -> offer.price() == null || !currency.equalsIgnoreCase(offer.price().currency()));
        boolean within = product.offers().stream()
                .map(Offer::price)
                .filter(Objects::nonNull)
                .filter(price -> currency.equalsIgnoreCase(price.currency()))
                .mapToLong(price -> price.minorUnits())
                .anyMatch(amount -> (filter.min() == null || amount >= filter.min())
                        && (filter.max() == null || amount <= filter.max()));
        return within || hasUnknown;
    }

    private boolean categoryEligible(CanonicalProduct product, CatalogSearchFilters filters) {
        if (filters == null || filters.categories() == null || filters.categories().isEmpty()) {
            return true;
        }
        Set<String> knownCategories = product.attributes().stream()
                .filter(this::categoryAttribute)
                .map(ProductAttribute::value)
                .map(ProductRankingFeatureExtractor::normalized)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (knownCategories.isEmpty()) {
            return true;
        }
        return filters.categories().stream()
                .map(ProductRankingFeatureExtractor::normalized)
                .filter(Objects::nonNull)
                .anyMatch(expected -> knownCategories.stream().anyMatch(value -> value.contains(expected)));
    }

    private boolean categoryAttribute(ProductAttribute attribute) {
        String group = normalized(attribute.group());
        String name = normalized(attribute.name());
        return group != null && group.contains("category") || name != null && name.contains("category");
    }

    private void add(
            List<ProductRankingExplanation.Feature> features,
            ProductRankingExplanation.Name name,
            Integer value,
            int weight
    ) {
        features.add(value == null
                ? ProductRankingExplanation.Feature.unknown(name, weight)
                : ProductRankingExplanation.Feature.available(name, value, weight, List.of()));
    }

    private Integer lexicalIntentFit(String productText, String intent) {
        Set<String> intentTokens = tokens(intent);
        if (intentTokens.isEmpty()) {
            return null;
        }
        Set<String> productTokens = tokens(productText);
        long matches = intentTokens.stream().filter(productTokens::contains).count();
        return (int) Math.round(matches * 10_000.0d / intentTokens.size());
    }

    private Integer preferenceFit(ProductText text, List<PreferenceSignal> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return null;
        }
        long totalWeight = preferences.stream().mapToLong(signal -> Math.abs(signal.weightBasisPoints())).sum();
        if (totalWeight == 0) {
            return null;
        }
        long contribution = preferences.stream()
                .filter(signal -> matches(text, signal))
                .mapToLong(PreferenceSignal::weightBasisPoints)
                .sum();
        return clamp((int) Math.round(5_000.0d + contribution * 5_000.0d / totalWeight));
    }

    private boolean matches(ProductText text, PreferenceSignal signal) {
        return switch (signal.type()) {
            case BRAND -> text.brand().contains(signal.normalizedValue());
            case CATEGORY -> text.category().contains(signal.normalizedValue());
            case MATERIAL -> text.materials().contains(signal.normalizedValue());
            case CERTIFICATION -> text.certifications().contains(signal.normalizedValue());
            case FILTER, QUERY -> text.searchable().contains(signal.normalizedValue());
        };
    }

    private Integer inventoryFit(ProductRankingContext.InventoryRelationship relationship) {
        if (relationship == null) {
            return null;
        }
        return switch (relationship) {
            case NONE -> 5_000;
            case DUPLICATE -> 1_000;
            case COMPLEMENT -> 8_000;
            case RESTOCK -> 10_000;
        };
    }

    private int quality(CanonicalProduct product) {
        int available = 0;
        available += product.title() == null ? 0 : 1;
        available += product.description() == null ? 0 : 1;
        available += product.media().isEmpty() ? 0 : 1;
        available += product.attributes().stream().anyMatch(attribute -> !commercialAttribute(attribute)) ? 1 : 0;
        available += product.materials().isEmpty() ? 0 : 1;
        available += product.certifications().isEmpty() ? 0 : 1;
        return (int) Math.round(available * 10_000.0d / 6.0d);
    }

    private Integer identityConfidence(CanonicalProduct product) {
        return product.identityEvidence().stream()
                .mapToInt(value -> value.confidenceBasisPoints())
                .max()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private int freshness(CanonicalProduct product, Instant rankedAt) {
        Instant latest = product.provenance().stream()
                .map(ResultProvenance::freshness)
                .map(value -> value.observedAt())
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);
        if (latest.isAfter(rankedAt)) {
            return 10_000;
        }
        long ageHours = Math.max(0, Duration.between(latest, rankedAt).toHours());
        return Math.max(0, 10_000 - (int) Math.min(10_000, ageHours * 14));
    }

    private int weightedScore(List<ProductRankingExplanation.Feature> features) {
        long weighted = 0;
        int weights = 0;
        for (ProductRankingExplanation.Feature feature : features) {
            if (feature.availability() == ProductRankingExplanation.Availability.AVAILABLE && feature.weight() > 0) {
                weighted += (long) feature.valueBasisPoints() * feature.weight();
                weights += feature.weight();
            }
        }
        return weights == 0 ? 5_000 : (int) Math.round(weighted / (double) weights);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(10_000, value));
    }

    private static Set<String> tokens(String value) {
        String normalized = normalized(value);
        if (normalized == null) {
            return Set.of();
        }
        return Stream.of(TOKEN_SPLIT.split(normalized))
                .filter(token -> token.length() > 1)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean commercialAttribute(ProductAttribute attribute) {
        String identity = Stream.of(attribute.group(), attribute.name())
                .filter(Objects::nonNull)
                .map(ProductRankingFeatureExtractor::normalized)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return Stream.of("affiliate", "commission", "commercial", "economics", "sponsored")
                .anyMatch(identity::contains);
    }

    private record ProductText(
            String searchable,
            String brand,
            String category,
            String materials,
            String certifications
    ) {

        private static ProductText from(CanonicalProduct product) {
            String attributes = product.attributes().stream()
                    .filter(attribute -> !commercialAttribute(attribute))
                    .flatMap(attribute -> Stream.of(attribute.group(), attribute.name(), attribute.value()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" "));
            String materials = product.materials().stream()
                    .map(value -> value.name())
                    .collect(Collectors.joining(" "));
            String certifications = product.certifications().stream()
                    .flatMap(value -> Stream.of(value.name(), value.issuer()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" "));
            String brand = product.attributes().stream()
                    .filter(attribute -> {
                        String name = normalized(attribute.name());
                        return name != null && (name.contains("brand") || name.contains("manufacturer"));
                    })
                    .map(ProductAttribute::value)
                    .collect(Collectors.joining(" "));
            String category = product.attributes().stream()
                    .filter(attribute -> {
                        String group = normalized(attribute.group());
                        String name = normalized(attribute.name());
                        return group != null && group.contains("category")
                                || name != null && name.contains("category");
                    })
                    .map(ProductAttribute::value)
                    .collect(Collectors.joining(" "));
            String searchable = Stream.of(
                            product.title(), product.description(), attributes, materials,
                            certifications, brand, category)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" "));
            return new ProductText(
                    Objects.requireNonNullElse(normalized(searchable), ""),
                    Objects.requireNonNullElse(normalized(brand), ""),
                    Objects.requireNonNullElse(normalized(category), ""),
                    Objects.requireNonNullElse(normalized(materials), ""),
                    Objects.requireNonNullElse(normalized(certifications), "")
            );
        }
    }

    record ScoredProduct(
            CanonicalProduct product,
            int scoreBasisPoints,
            ProductRankingExplanation explanation
    ) {
    }
}
