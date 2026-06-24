package com.meant.api.module.user.service;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class UserProductPreferenceMatchCuratorService {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final int TAKE_LIMIT = 220;
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "avoid", "be", "by", "can", "for", "from", "goods", "in",
            "is", "labeled", "made", "no", "not", "of", "or", "other", "prefer", "products", "require",
            "that", "the", "to", "when", "where", "with", "without"
    );
    private static final Set<String> AVOID_PREFIXES = Set.of("no", "avoid", "without");

    public Map<String, UserProductRecommendationExplanationResult> curate(
            List<UserProductSearchProductSnapshot> products,
            UserSettingsResult settings,
            Map<String, UserProductRecommendationExplanationResult> explanations,
            Map<String, UserInventoryRecommendationSignal> inventorySignals
    ) {
        Map<String, ShoppingFilterResult> filtersById = filtersById(settings);
        Map<String, UserProductRecommendationExplanationResult> safeExplanations =
                explanations == null ? Map.of() : explanations;
        Map<String, UserInventoryRecommendationSignal> safeInventorySignals =
                inventorySignals == null ? Map.of() : inventorySignals;
        Map<String, UserProductRecommendationExplanationResult> curated = new LinkedHashMap<>();
        for (UserProductSearchProductSnapshot product : products) {
            UserInventoryRecommendationSignal inventorySignal = safeInventorySignals.get(product.productKey());
            UserProductRecommendationExplanationResult explanation = safeExplanations.get(product.productKey());
            if (explanation == null) {
                explanation = UserProductRecommendationExplanationResult.fallback(
                        product.productKey(),
                        product.productHash(),
                        inventorySignal
                );
            }
            ProductEvidence evidence = ProductEvidence.from(product.product());
            List<String> matchedFilterIds = curatedMatches(explanation, filtersById, evidence);
            List<String> missedFilterIds = curatedMisses(explanation, filtersById, evidence, matchedFilterIds);
            curated.put(product.productKey(), new UserProductRecommendationExplanationResult(
                    product.productKey(),
                    product.productHash(),
                    curatorTake(matchedFilterIds, missedFilterIds, filtersById, inventorySignal),
                    matchedFilterIds,
                    missedFilterIds,
                    relationship(inventorySignal),
                    inventorySignal == null ? null : inventorySignal.inventoryItemId(),
                    inventorySignal == null ? null : inventorySignal.inventoryItemName()
            ));
        }
        return curated;
    }

    private Map<String, ShoppingFilterResult> filtersById(UserSettingsResult settings) {
        if (settings == null || settings.filters() == null) {
            return Map.of();
        }
        return settings.filters().stream()
                .filter(filter -> filter != null && filter.id() != null)
                .collect(Collectors.toMap(
                        ShoppingFilterResult::id,
                        filter -> filter,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<String> curatedMatches(
            UserProductRecommendationExplanationResult explanation,
            Map<String, ShoppingFilterResult> filtersById,
            ProductEvidence evidence
    ) {
        LinkedHashSet<String> matches = safeList(explanation.matchedFilterIds()).stream()
                .filter(filtersById::containsKey)
                .filter(filterId -> supportsMatch(filtersById.get(filterId), evidence))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        filtersById.forEach((filterId, filter) -> {
            if (!matches.contains(filterId) && supportsMatch(filter, evidence)) {
                matches.add(filterId);
            }
        });
        return List.copyOf(matches);
    }

    private List<String> curatedMisses(
            UserProductRecommendationExplanationResult explanation,
            Map<String, ShoppingFilterResult> filtersById,
            ProductEvidence evidence,
            List<String> matchedFilterIds
    ) {
        Set<String> matched = Set.copyOf(matchedFilterIds);
        return safeList(explanation.missedFilterIds()).stream()
                .filter(filtersById::containsKey)
                .filter(filterId -> !matched.contains(filterId))
                .filter(filterId -> supportsMiss(filtersById.get(filterId), evidence))
                .distinct()
                .toList();
    }

    private boolean supportsMatch(ShoppingFilterResult filter, ProductEvidence evidence) {
        String id = filter.id();
        if ("highly-rated".equals(id)) {
            return evidence.ratingScore() != null && evidence.ratingScore() >= 4.2d
                    && evidence.reviewCount() != null && evidence.reviewCount() > 0;
        }
        if ("many-reviews".equals(id)) {
            return evidence.reviewCount() != null && evidence.reviewCount() >= 100;
        }
        if ("organic".equals(id)) {
            return evidence.containsAny(List.of("organic", "certified organic", "usda organic", "gots"));
        }
        if ("natural-materials".equals(id)) {
            return evidence.containsAny(List.of(
                    "natural fiber", "natural fibre", "cotton", "organic cotton", "wool", "merino", "linen", "silk",
                    "hemp", "bamboo"
            ));
        }
        if ("sustainable-brands".equals(id)) {
            return evidence.containsAny(List.of(
                    "sustainable", "sustainability", "responsibly made", "ethical", "fair trade", "b corp",
                    "recycled", "carbon neutral", "gots"
            ));
        }
        if (isAvoidFilter(filter)) {
            return hasFreeEvidence(filter, evidence);
        }
        return hasDirectEvidence(filter, evidence);
    }

    private boolean supportsMiss(ShoppingFilterResult filter, ProductEvidence evidence) {
        if (isAvoidFilter(filter)) {
            return containsAvoidedTerm(filter, evidence) && !hasFreeEvidence(filter, evidence);
        }
        return false;
    }

    private boolean isAvoidFilter(ShoppingFilterResult filter) {
        return "avoid".equals(filter.polarity()) || AVOID_PREFIXES.contains(firstToken(filter.label()));
    }

    private boolean hasDirectEvidence(ShoppingFilterResult filter, ProductEvidence evidence) {
        List<String> phrases = Stream.of(filter.id(), filter.label())
                .map(UserProductPreferenceMatchCuratorService::normalized)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.replace('-', ' '))
                .distinct()
                .toList();
        if (phrases.stream().anyMatch(evidence::contains)) {
            return true;
        }
        List<String> tokens = evidenceTokens(filter);
        return !tokens.isEmpty() && tokens.stream().allMatch(evidence::containsToken);
    }

    private boolean hasFreeEvidence(ShoppingFilterResult filter, ProductEvidence evidence) {
        List<String> terms = avoidedTerms(filter);
        return terms.stream().anyMatch(term -> evidence.contains("no " + term)
                || evidence.contains("without " + term)
                || evidence.contains(term + " free")
                || evidence.contains(term + "-free")
                || evidence.contains("free of " + term));
    }

    private boolean containsAvoidedTerm(ShoppingFilterResult filter, ProductEvidence evidence) {
        return avoidedTerms(filter).stream().anyMatch(evidence::contains);
    }

    private List<String> evidenceTokens(ShoppingFilterResult filter) {
        return Stream.of(filter.id(), filter.label())
                .flatMap(value -> tokens(value).stream())
                .filter(token -> token.length() > 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .distinct()
                .toList();
    }

    private List<String> avoidedTerms(ShoppingFilterResult filter) {
        List<String> terms = evidenceTokens(filter).stream()
                .filter(token -> !AVOID_PREFIXES.contains(token))
                .toList();
        if (!terms.isEmpty()) {
            return terms;
        }
        return tokens(filter.description()).stream()
                .filter(token -> token.length() > 3)
                .filter(token -> !STOP_WORDS.contains(token))
                .distinct()
                .toList();
    }

    private String firstToken(String value) {
        return tokens(value).stream().findFirst().orElse("");
    }

    private String curatorTake(
            List<String> matchedFilterIds,
            List<String> missedFilterIds,
            Map<String, ShoppingFilterResult> filtersById,
            UserInventoryRecommendationSignal inventorySignal
    ) {
        if (inventorySignal != null && inventorySignal.relationship() == UserInventoryRecommendationRelationship.DUPLICATE) {
            return limit("Curator flags this as close to %s, so compare before buying."
                    .formatted(inventorySignal.inventoryItemName() == null ? "something you own" : inventorySignal.inventoryItemName()));
        }
        String matched = filterLabelList(matchedFilterIds, filtersById);
        String missed = filterLabelList(missedFilterIds, filtersById);
        if (!matched.isBlank() && !missed.isBlank()) {
            return limit("Curator confirmed %s from catalog data; check %s before deciding."
                    .formatted(matched, missed));
        }
        if (!matched.isBlank()) {
            return limit("Curator confirmed %s from catalog data.".formatted(matched));
        }
        if (!missed.isBlank()) {
            return limit("Curator found a trade-off around %s in the catalog data.".formatted(missed));
        }
        return "Curator found no confirmed preference matches yet; review the catalog details and offers.";
    }

    private String filterLabelList(
            List<String> filterIds,
            Map<String, ShoppingFilterResult> filtersById
    ) {
        return filterIds.stream()
                .map(filtersById::get)
                .filter(filter -> filter != null)
                .map(ShoppingFilterResult::label)
                .filter(label -> label != null && !label.isBlank())
                .limit(3)
                .collect(Collectors.joining(", "));
    }

    private String limit(String value) {
        return value.length() <= TAKE_LIMIT ? value : value.substring(0, TAKE_LIMIT).trim();
    }

    private UserInventoryRecommendationRelationship relationship(UserInventoryRecommendationSignal signal) {
        return signal == null || signal.relationship() == null
                ? UserInventoryRecommendationRelationship.NONE
                : signal.relationship();
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return SPACE_PATTERN.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC)
                        .trim()
                        .toLowerCase(Locale.ROOT)
                        .replace('-', ' '))
                .replaceAll(" ");
    }

    private static List<String> tokens(String value) {
        String normalized = normalized(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        return TOKEN_SPLIT_PATTERN.splitAsStream(normalized)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record ProductEvidence(
            String text,
            Set<String> tokens,
            Double ratingScore,
            Integer reviewCount
    ) {

        static ProductEvidence from(MerchantSemanticProductResult product) {
            String text = normalized(Stream.of(
                            product.title(),
                            plainText(product.descriptionHtml()),
                            plainText(product.detailDescription()),
                            product.merchantName(),
                            product.merchantDomain(),
                            join(stream(product.categories())
                                    .filter(category -> category != null)
                                    .map(ProductCatalogCategory::value)),
                            join(stream(product.certifications())),
                            join(stream(product.materials())),
                            join(stream(product.collections())),
                            join(stream(product.attributes())
                                    .filter(attribute -> attribute != null)
                                    .flatMap(attribute -> Stream.of(
                                            attribute.name(),
                                            attribute.value()
                                    )))
                    )
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.joining(" ")));
            return new ProductEvidence(
                    " " + text + " ",
                    TOKEN_SPLIT_PATTERN.splitAsStream(text)
                            .filter(token -> !token.isBlank())
                            .collect(Collectors.toSet()),
                    product.ratingScore(),
                    product.reviewCount()
            );
        }

        private boolean contains(String phrase) {
            String normalized = normalized(phrase);
            return !normalized.isBlank() && text.contains(" " + normalized + " ");
        }

        private boolean containsAny(List<String> phrases) {
            return phrases.stream().anyMatch(this::contains);
        }

        private boolean containsToken(String token) {
            return tokens.contains(token);
        }

        private static String plainText(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            return HTML_TAG_PATTERN.matcher(value).replaceAll(" ");
        }

        private static <T> String join(Stream<T> values) {
            return values
                    .filter(value -> value != null)
                    .map(Object::toString)
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.joining(" "));
        }

        private static <T> Stream<T> stream(List<T> values) {
            return values == null ? Stream.empty() : values.stream();
        }
    }
}
