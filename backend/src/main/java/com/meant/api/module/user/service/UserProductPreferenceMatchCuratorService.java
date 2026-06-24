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
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s-]+");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^\\p{L}\\p{N}]+");
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
        Map<String, CuratorFilter> filtersById = curatorFiltersById(settings);
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

    private Map<String, CuratorFilter> curatorFiltersById(UserSettingsResult settings) {
        if (settings == null || settings.filters() == null) {
            return Map.of();
        }
        return settings.filters().stream()
                .filter(filter -> filter != null && filter.id() != null)
                .collect(Collectors.toMap(
                        ShoppingFilterResult::id,
                        CuratorFilter::from,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<String> curatedMatches(
            UserProductRecommendationExplanationResult explanation,
            Map<String, CuratorFilter> filtersById,
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
            Map<String, CuratorFilter> filtersById,
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

    private boolean supportsMatch(CuratorFilter filter, ProductEvidence evidence) {
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
        if (filter.avoid()) {
            return hasFreeEvidence(filter, evidence);
        }
        return hasDirectEvidence(filter, evidence);
    }

    private boolean supportsMiss(CuratorFilter filter, ProductEvidence evidence) {
        if (filter.avoid()) {
            return containsAvoidedTerm(filter, evidence) && !hasFreeEvidence(filter, evidence);
        }
        return false;
    }

    private boolean hasDirectEvidence(CuratorFilter filter, ProductEvidence evidence) {
        if (filter.phrases().stream().anyMatch(evidence::contains)) {
            return true;
        }
        return filter.evidenceTokenGroups().stream()
                .anyMatch(tokens -> tokens.stream().allMatch(evidence::containsToken));
    }

    private boolean hasFreeEvidence(CuratorFilter filter, ProductEvidence evidence) {
        return filter.avoidedTerms().stream().anyMatch(term -> evidence.contains("no " + term)
                || evidence.contains("without " + term)
                || evidence.contains(term + " free")
                || evidence.contains(term + "-free")
                || evidence.contains("free of " + term));
    }

    private boolean containsAvoidedTerm(CuratorFilter filter, ProductEvidence evidence) {
        return filter.avoidedTerms().stream().anyMatch(evidence::contains);
    }

    private static List<String> evidenceTokens(String value) {
        return tokens(value).stream()
                .filter(UserProductPreferenceMatchCuratorService::isMeaningfulEvidenceToken)
                .filter(token -> !STOP_WORDS.contains(token))
                .distinct()
                .toList();
    }

    private static boolean isMeaningfulEvidenceToken(String token) {
        int length = token.codePointCount(0, token.length());
        return length > 2 || (length > 1 && token.codePoints().anyMatch(codePoint -> codePoint > 0x7f));
    }

    private static String firstToken(String value) {
        return tokens(value).stream().findFirst().orElse("");
    }

    private String curatorTake(
            List<String> matchedFilterIds,
            List<String> missedFilterIds,
            Map<String, CuratorFilter> filtersById,
            UserInventoryRecommendationSignal inventorySignal
    ) {
        if (inventorySignal != null && inventorySignal.relationship() == UserInventoryRecommendationRelationship.DUPLICATE) {
            return limit("This looks close to %s, so compare before buying."
                    .formatted(inventorySignal.inventoryItemName() == null
                            ? "something you already own"
                            : inventorySignal.inventoryItemName() + " you already own"));
        }
        String matched = preferenceSummary(matchedFilterIds, filtersById);
        String missed = preferenceSummary(missedFilterIds, filtersById);
        if (!matched.isBlank() && !missed.isBlank()) {
            return limit("This matches %s, but check whether it fits %s before deciding."
                    .formatted(matched, missed));
        }
        if (!matched.isBlank()) {
            return limit("This matches %s.".formatted(matched));
        }
        if (!missed.isBlank()) {
            return limit("Check whether this fits %s before deciding.".formatted(missed));
        }
        return "No preference matches are confirmed yet; review the details and offers.";
    }

    private String preferenceSummary(
            List<String> filterIds,
            Map<String, CuratorFilter> filtersById
    ) {
        List<PreferenceTarget> targets = filterIds.stream()
                .map(filtersById::get)
                .filter(filter -> filter != null)
                .map(CuratorFilter::label)
                .filter(label -> label != null && !label.isBlank())
                .map(UserProductPreferenceMatchCuratorService::preferenceTarget)
                .filter(target -> !target.text().isBlank())
                .limit(3)
                .toList();
        if (targets.isEmpty()) {
            return "";
        }
        List<String> preferred = targets.stream()
                .filter(target -> target.kind() == PreferenceTargetKind.PREFER)
                .map(PreferenceTarget::text)
                .toList();
        List<String> avoided = targets.stream()
                .filter(target -> target.kind() == PreferenceTargetKind.AVOID)
                .map(PreferenceTarget::text)
                .toList();
        List<String> parts = Stream.of(
                        preferred.isEmpty() ? "" : "for " + humanList(preferred),
                        avoided.isEmpty() ? "" : "to avoid " + humanList(avoided)
                )
                .filter(part -> !part.isBlank())
                .toList();
        String preferenceWord = targets.size() == 1 ? "preference" : "preferences";
        return "your " + preferenceWord + " " + humanList(parts);
    }

    private static PreferenceTarget preferenceTarget(String label) {
        String normalized = SPACE_PATTERN.matcher(label.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
        if (normalized.startsWith("no ")) {
            return new PreferenceTarget(PreferenceTargetKind.AVOID, normalized.substring(3).trim());
        }
        if (normalized.startsWith("avoid ")) {
            return new PreferenceTarget(PreferenceTargetKind.AVOID, normalized.substring(6).trim());
        }
        if (normalized.startsWith("without ")) {
            return new PreferenceTarget(PreferenceTargetKind.AVOID, normalized.substring(8).trim());
        }
        if (normalized.startsWith("prefer ")) {
            return new PreferenceTarget(PreferenceTargetKind.PREFER, normalized.substring(7).trim());
        }
        return new PreferenceTarget(PreferenceTargetKind.PREFER, normalized);
    }

    private static String humanList(List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.getFirst();
        }
        if (items.size() == 2) {
            return items.get(0) + " and " + items.get(1);
        }
        return String.join(", ", items.subList(0, items.size() - 1))
                + ", and "
                + items.getLast();
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
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
        String cleaned = PUNCTUATION_PATTERN.matcher(normalized)
                .replaceAll(" ")
                .replace('-', ' ');
        return SPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
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

    private record CuratorFilter(
            ShoppingFilterResult source,
            List<String> phrases,
            List<List<String>> evidenceTokenGroups,
            List<String> avoidedTerms,
            boolean avoid
    ) {

        static CuratorFilter from(ShoppingFilterResult filter) {
            List<String> phrases = Stream.of(filter.id(), filter.label())
                    .map(UserProductPreferenceMatchCuratorService::normalized)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
            List<List<String>> evidenceTokenGroups = Stream.of(filter.id(), filter.label())
                    .map(UserProductPreferenceMatchCuratorService::evidenceTokens)
                    .filter(tokens -> !tokens.isEmpty())
                    .distinct()
                    .toList();
            List<String> evidenceTokens = evidenceTokenGroups.stream()
                    .flatMap(List::stream)
                    .distinct()
                    .toList();
            List<String> avoidedTerms = evidenceTokens.stream()
                    .filter(token -> !AVOID_PREFIXES.contains(token))
                    .toList();
            if (avoidedTerms.isEmpty()) {
                avoidedTerms = tokens(filter.description()).stream()
                        .filter(token -> token.length() > 3)
                        .filter(token -> !STOP_WORDS.contains(token))
                        .distinct()
                        .toList();
            }
            boolean avoid = "avoid".equals(filter.polarity()) || AVOID_PREFIXES.contains(firstToken(filter.label()));
            return new CuratorFilter(filter, phrases, evidenceTokenGroups, avoidedTerms, avoid);
        }

        private String id() {
            return source.id();
        }

        private String label() {
            return source.label();
        }
    }

    private enum PreferenceTargetKind {
        PREFER,
        AVOID
    }

    private record PreferenceTarget(
            PreferenceTargetKind kind,
            String text
    ) {
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
