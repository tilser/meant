package com.meant.api.module.user.service;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class UserProductPreferenceMatchCuratorService {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern NEGATIVE_CONTRACTION_PATTERN = Pattern.compile("\\b[\\p{L}]+n['’]t\\b");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s-]+");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int TAKE_LIMIT = 220;
    private static final String NO_CONFIRMED_PREFERENCE_TAKE =
            "No preference matches are confirmed yet; review the details and offers.";
    private static final String SEARCH_RELEVANCE_TAKE =
            "This looks relevant to your search based on the available product details.";
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "avoid", "be", "by", "can", "for", "from", "goods", "in",
            "is", "labeled", "made", "no", "not", "of", "or", "other", "prefer", "products", "require",
            "that", "the", "to", "when", "where", "with", "without"
    );
    private static final Set<String> AVOID_PREFIXES = Set.of("no", "avoid", "without");
    private static final List<String> ORGANIC_EVIDENCE =
            List.of("organic", "certified organic", "usda organic", "gots");
    private static final List<String> NATURAL_MATERIAL_EVIDENCE = List.of(
            "natural fiber", "natural fibre", "cotton", "organic cotton", "wool", "merino", "linen", "silk",
            "hemp", "bamboo"
    );
    private static final List<String> SUSTAINABILITY_EVIDENCE = List.of(
            "sustainable", "sustainability", "responsibly made", "ethical", "fair trade", "b corp",
            "recycled", "carbon neutral", "gots"
    );

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
                    curatorTake(
                            matchedFilterIds,
                            missedFilterIds,
                            filtersById,
                            inventorySignal,
                            explanation.whyMeantForYou()
                    ),
                    matchedFilterIds,
                    missedFilterIds,
                    relationship(inventorySignal),
                    inventorySignal == null ? null : inventorySignal.inventoryItemId(),
                    inventorySignal == null ? null : inventorySignal.inventoryItemName()
            ));
        }
        return curated;
    }

    public Map<String, UserCanonicalProductPersonalizationResult> curateCanonical(
            List<CanonicalProduct> products,
            UserSettingsResult settings
    ) {
        Map<String, CuratorFilter> filtersById = curatorFiltersById(settings);
        Map<String, UserCanonicalProductPersonalizationResult> curated = new LinkedHashMap<>();
        for (CanonicalProduct product : safeList(products)) {
            if (product == null) {
                continue;
            }
            ProductEvidence evidence = ProductEvidence.from(product);
            Map<String, String> matchedFactsByFilterId = new LinkedHashMap<>();
            filtersById.forEach((filterId, filter) -> {
                String fact = confirmedMatchFact(filter, evidence);
                if (fact != null) {
                    matchedFactsByFilterId.put(filterId, fact);
                }
            });
            List<String> matchedFilterIds = List.copyOf(matchedFactsByFilterId.keySet());
            Set<String> matched = Set.copyOf(matchedFilterIds);
            Map<String, String> missedFactsByFilterId = new LinkedHashMap<>();
            filtersById.forEach((filterId, filter) -> {
                if (matched.contains(filterId)) {
                    return;
                }
                String fact = confirmedMissFact(filter, evidence);
                if (fact != null) {
                    missedFactsByFilterId.put(filterId, fact);
                }
            });
            List<String> missedFilterIds = List.copyOf(missedFactsByFilterId.keySet());
            Set<String> classified = Stream.concat(
                            matchedFilterIds.stream(),
                            missedFilterIds.stream()
                    )
                    .collect(Collectors.toSet());
            List<String> unknownFilterIds = filtersById.keySet().stream()
                    .filter(filterId -> !classified.contains(filterId))
                    .sorted(Comparator
                            .comparingInt((String filterId) -> unknownPriority(filtersById.get(filterId)))
                            .thenComparingInt(filterId -> displayOrder(filtersById.get(filterId))))
                    .toList();
            List<String> hardConstraintFilterIds = filtersById.entrySet().stream()
                    .filter(entry -> entry.getValue().hardConstraint())
                    .map(Map.Entry::getKey)
                    .toList();
            curated.put(product.key(), new UserCanonicalProductPersonalizationResult(
                    canonicalTake(
                            matchedFactsByFilterId,
                            missedFactsByFilterId,
                            unknownFilterIds,
                            hardConstraintFilterIds,
                            filtersById
                    ),
                    matchedFilterIds,
                    missedFilterIds,
                    unknownFilterIds,
                    hardConstraintFilterIds
            ));
        }
        return Map.copyOf(curated);
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
            return evidence.containsAny(ORGANIC_EVIDENCE);
        }
        if ("natural-materials".equals(id)) {
            return evidence.containsAny(NATURAL_MATERIAL_EVIDENCE);
        }
        if ("sustainable-brands".equals(id)) {
            return evidence.containsAny(SUSTAINABILITY_EVIDENCE);
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
                .anyMatch(evidence::containsAllTokens);
    }

    private boolean hasFreeEvidence(CuratorFilter filter, ProductEvidence evidence) {
        return filter.avoidedTerms().stream().anyMatch(term -> hasFreeEvidence(term, evidence));
    }

    private boolean hasFreeEvidence(String term, ProductEvidence evidence) {
        return evidence.contains("no " + term)
                || evidence.contains("without " + term)
                || evidence.contains(term + " free")
                || evidence.contains(term + "-free")
                || evidence.contains("free of " + term);
    }

    private boolean containsAvoidedTerm(CuratorFilter filter, ProductEvidence evidence) {
        return filter.avoidedTerms().stream().anyMatch(evidence::contains);
    }

    private String confirmedMatchFact(CuratorFilter filter, ProductEvidence evidence) {
        if (!supportsMatch(filter, evidence)) {
            return null;
        }
        if (filter.avoid()) {
            return confirmedFreeFact(filter);
        }
        if ("organic".equals(filter.id())) {
            return firstEvidenceLabel(ORGANIC_EVIDENCE, evidence);
        }
        if ("natural-materials".equals(filter.id())) {
            return firstEvidenceLabel(NATURAL_MATERIAL_EVIDENCE, evidence);
        }
        if ("sustainable-brands".equals(filter.id())) {
            return firstEvidenceLabel(SUSTAINABILITY_EVIDENCE, evidence);
        }
        String label = filter.label() == null ? filter.id() : filter.label();
        return preferenceTarget(label).text();
    }

    private String confirmedMissFact(CuratorFilter filter, ProductEvidence evidence) {
        if (!filter.avoid() || hasFreeEvidence(filter, evidence)) {
            return null;
        }
        String target = avoidTarget(filter);
        return target.isBlank() || !evidence.contains(target) ? null : target;
    }

    private String confirmedFreeFact(CuratorFilter filter) {
        String target = avoidTarget(filter);
        if (target.isBlank()) {
            return null;
        }
        String normalizedLabel = normalized(filter.label() == null ? filter.id() : filter.label());
        return normalizedLabel.startsWith("no ") ? "no " + target : target + "-free";
    }

    private String avoidTarget(CuratorFilter filter) {
        String value = normalized(filter.label() == null ? filter.id() : filter.label());
        for (String prefix : List.of("no ", "avoid ", "without ")) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length()).trim();
                break;
            }
        }
        return value.endsWith(" free")
                ? value.substring(0, value.length() - " free".length()).trim()
                : value;
    }

    private static String firstEvidenceLabel(List<String> candidates, ProductEvidence evidence) {
        return candidates.stream()
                .filter(evidence::contains)
                .map(candidate -> "gots".equals(candidate) ? "GOTS certification" : candidate)
                .findFirst()
                .orElse(null);
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

    private String canonicalTake(
            Map<String, String> matchedEvidence,
            Map<String, String> missedEvidence,
            List<String> unknownFilterIds,
            List<String> hardConstraintFilterIds,
            Map<String, CuratorFilter> filtersById
    ) {
        List<String> matchedFacts = matchedEvidence.values().stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(3)
                .toList();
        List<String> missedFacts = missedEvidence.values().stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(3)
                .toList();
        List<String> unknownLabels = filterLabels(unknownFilterIds, filtersById);
        List<String> hardConstraints = hardConstraintFilterIds.stream()
                .map(filterId -> hardConstraintSummary(
                        filterId,
                        matchedEvidence,
                        missedEvidence,
                        filtersById
                ))
                .filter(value -> value != null && !value.isBlank())
                .limit(3)
                .toList();
        List<String> parts = new ArrayList<>();
        if (!matchedFacts.isEmpty()) {
            parts.add("Matched: " + humanList(matchedFacts) + ".");
        }
        if (!missedFacts.isEmpty()) {
            parts.add("Conflicts: " + humanList(missedFacts) + ".");
        }
        if (!unknownLabels.isEmpty()) {
            parts.add("Unknown: " + humanList(unknownLabels) + ".");
        }
        if (!hardConstraints.isEmpty()) {
            parts.add("Hard constraints: " + humanList(hardConstraints) + ".");
        }
        return parts.isEmpty() ? fallbackTake(null) : limit(String.join(" ", parts));
    }

    private List<String> filterLabels(
            List<String> filterIds,
            Map<String, CuratorFilter> filtersById
    ) {
        return filterIds.stream()
                .map(filtersById::get)
                .filter(Objects::nonNull)
                .map(filter -> filter.label() == null ? filter.id() : filter.label())
                .filter(label -> !label.isBlank())
                .limit(3)
                .toList();
    }

    private int unknownPriority(CuratorFilter filter) {
        if (filter == null) {
            return 5;
        }
        if (filter.hardConstraint()) {
            return 0;
        }
        if ("sustainability".equals(filter.source().category())) {
            return 1;
        }
        if ("highly-rated".equals(filter.id()) || "many-reviews".equals(filter.id())) {
            return 2;
        }
        if ("interests".equals(filter.source().category())) {
            return 3;
        }
        return 4;
    }

    private int displayOrder(CuratorFilter filter) {
        return filter == null || filter.source().displayOrder() == null
                ? Integer.MAX_VALUE
                : filter.source().displayOrder();
    }

    private String hardConstraintSummary(
            String filterId,
            Map<String, String> matchedEvidence,
            Map<String, String> missedEvidence,
            Map<String, CuratorFilter> filtersById
    ) {
        CuratorFilter filter = filtersById.get(filterId);
        if (filter == null) {
            return null;
        }
        String label = filter.label() == null ? filter.id() : filter.label();
        String status = matchedEvidence.containsKey(filterId)
                ? "matched"
                : missedEvidence.containsKey(filterId) ? "conflict" : "unknown";
        return label + " (" + status + ")";
    }

    private String curatorTake(
            List<String> matchedFilterIds,
            List<String> missedFilterIds,
            Map<String, CuratorFilter> filtersById,
            UserInventoryRecommendationSignal inventorySignal,
            String explanationTake
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
        return fallbackTake(explanationTake);
    }

    private String fallbackTake(String explanationTake) {
        if (explanationTake != null) {
            String trimmed = SPACE_PATTERN.matcher(explanationTake.trim()).replaceAll(" ");
            if (!trimmed.isBlank() && !NO_CONFIRMED_PREFERENCE_TAKE.equals(trimmed)) {
                return limit(trimmed);
            }
        }
        return SEARCH_RELEVANCE_TAKE;
    }

    private String preferenceSummary(
            List<String> filterIds,
            Map<String, CuratorFilter> filtersById
    ) {
        List<PreferenceTarget> targets = filterIds.stream()
                .map(filtersById::get)
                .filter(Objects::nonNull)
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
        String expanded = NEGATIVE_CONTRACTION_PATTERN.matcher(normalized).replaceAll(" not ");
        String cleaned = PUNCTUATION_PATTERN.matcher(expanded)
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
            boolean avoid,
            boolean hardConstraint
    ) {

        static CuratorFilter from(ShoppingFilterResult filter) {
            List<String> phrases = Stream.of(filter.id(), filter.label())
                    .map(UserProductPreferenceMatchCuratorService::normalized)
                    .filter(value -> !value.isBlank())
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
            boolean hardConstraint = avoid || "require".equals(filter.polarity());
            return new CuratorFilter(filter, phrases, evidenceTokenGroups, avoidedTerms, avoid, hardConstraint);
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
            List<String> statements,
            Double ratingScore,
            Integer reviewCount
    ) {

        private static final int NEGATION_LOOKBACK_TOKENS = 5;
        private static final Set<String> NEGATION_TOKENS = Set.of(
                "no", "not", "never", "without", "non", "false", "absent", "lacks", "lacking",
                "excludes", "excluding"
        );
        private static final Set<String> NEGATIVE_SUFFIX_TOKENS = Set.of(
                "no", "not", "false", "absent", "negative"
        );
        private static final Set<String> NEGATIVE_ATTRIBUTE_VALUES = Set.of(
                "no", "false", "none", "absent", "negative", "0"
        );

        ProductEvidence {
            statements = statements == null
                    ? List.of()
                    : statements.stream()
                            .map(UserProductPreferenceMatchCuratorService::normalized)
                            .filter(value -> !value.isBlank())
                            .map(value -> " " + value + " ")
                            .toList();
        }

        static ProductEvidence from(MerchantSemanticProductResult product) {
            List<String> statements = new ArrayList<>();
            add(statements, product.title());
            add(statements, plainText(product.descriptionHtml()));
            add(statements, plainText(product.detailDescription()));
            add(statements, product.merchantName());
            add(statements, product.merchantDomain());
            stream(product.categories())
                    .filter(Objects::nonNull)
                    .map(ProductCatalogCategory::value)
                    .forEach(value -> add(statements, value));
            stream(product.certifications()).forEach(value -> add(statements, value));
            stream(product.materials()).forEach(value -> add(statements, value));
            stream(product.collections()).forEach(value -> add(statements, value));
            stream(product.attributes())
                    .filter(Objects::nonNull)
                    .map(attribute -> attributeStatement(null, attribute.name(), attribute.value()))
                    .forEach(value -> add(statements, value));
            return new ProductEvidence(statements, product.ratingScore(), product.reviewCount());
        }

        static ProductEvidence from(CanonicalProduct product) {
            List<String> statements = new ArrayList<>();
            add(statements, product.title());
            add(statements, plainText(product.description()));
            product.attributes().stream()
                    .map(attribute -> attributeStatement(
                            attribute.group(),
                            attribute.name(),
                            attribute.value()
                    ))
                    .forEach(value -> add(statements, value));
            product.materials().forEach(material -> add(statements, material.name()));
            product.certifications().forEach(certification -> {
                add(statements, certification.name());
                add(statements, certification.issuer());
            });
            return new ProductEvidence(statements, null, null);
        }

        private boolean contains(String phrase) {
            String expected = normalized(phrase);
            return !expected.isBlank() && statements.stream()
                    .anyMatch(statement -> containsSupported(statement, expected));
        }

        private boolean containsAny(List<String> phrases) {
            return phrases.stream().anyMatch(this::contains);
        }

        private boolean containsAllTokens(List<String> expectedTokens) {
            if (expectedTokens == null || expectedTokens.isEmpty()) {
                return false;
            }
            return statements.stream().anyMatch(statement -> {
                Set<String> actualTokens = TOKEN_SPLIT_PATTERN.splitAsStream(statement)
                        .filter(token -> !token.isBlank())
                        .collect(Collectors.toSet());
                return actualTokens.containsAll(expectedTokens)
                        && expectedTokens.stream().allMatch(token -> containsSupported(statement, token));
            });
        }

        private static boolean containsSupported(String statement, String expected) {
            String needle = " " + expected + " ";
            int offset = 0;
            while (offset < statement.length()) {
                int index = statement.indexOf(needle, offset);
                if (index < 0) {
                    return false;
                }
                if (explicitNegativePhrase(expected)
                        || !negated(statement, index, index + needle.length())) {
                    return true;
                }
                offset = index + 1;
            }
            return false;
        }

        private static boolean explicitNegativePhrase(String expected) {
            return expected.startsWith("no ")
                    || expected.startsWith("not ")
                    || expected.startsWith("without ")
                    || expected.startsWith("free of ");
        }

        private static boolean negated(String statement, int matchStart, int matchEnd) {
            List<String> before = tokens(statement.substring(0, matchStart));
            int from = Math.max(0, before.size() - NEGATION_LOOKBACK_TOKENS);
            if (before.subList(from, before.size()).stream().anyMatch(NEGATION_TOKENS::contains)) {
                return true;
            }
            return tokens(statement.substring(matchEnd)).stream()
                    .limit(2)
                    .anyMatch(NEGATIVE_SUFFIX_TOKENS::contains);
        }

        private static String attributeStatement(String group, String name, String value) {
            String normalizedName = normalized(name);
            String normalizedValue = normalized(value);
            if (NEGATIVE_ATTRIBUTE_VALUES.contains(normalizedValue)) {
                String subject = normalizedName.startsWith("contains ")
                        ? normalizedName.substring("contains ".length()).trim()
                        : normalizedName;
                String prefix = normalizedName.endsWith(" free") ? "not " : "no ";
                return Stream.of(prefix + subject, group)
                        .filter(part -> part != null && !part.isBlank())
                        .collect(Collectors.joining(" "));
            }
            return Stream.of(group, name, value)
                    .filter(part -> part != null && !part.isBlank())
                    .collect(Collectors.joining(" "));
        }

        private static void add(List<String> statements, Object value) {
            if (value != null && !value.toString().isBlank()) {
                statements.add(value.toString());
            }
        }

        private static String plainText(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            return HTML_TAG_PATTERN.matcher(value).replaceAll(" ");
        }

        private static <T> Stream<T> stream(List<T> values) {
            return values == null ? Stream.empty() : values.stream();
        }
    }
}
