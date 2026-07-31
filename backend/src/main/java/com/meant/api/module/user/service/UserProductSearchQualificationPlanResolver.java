package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.user.constant.UserCurrency;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.constant.UserProductSearchQueryLimits;
import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Applies server-owned policy, provenance checks, and multi-turn accumulation to an LLM candidate plan. */
@Component
public class UserProductSearchQualificationPlanResolver {

    private static final Set<String> EFFECTIVE_QUERY_CONNECTORS = Set.of(
            "a", "an", "and", "for", "from", "in", "made", "of", "on", "or", "the", "to", "with"
    );
    private static final Pattern SHIPS_TO_QUERY_PATTERN = Pattern.compile(
            "(?iu)\\b(?:ship|ships|shipped|shipping|deliver|delivered|delivery)"
                    + "\\s+(?:it\\s+)?to\\b|\\b(?:shipping\\s+)?destination\\b"
                    + "|\\b(?:based|located)\\s+in\\b"
                    + "|\\bi\\s+(?:am|live)\\s+in\\b|\\bi['’]m\\s+in\\b"
    );
    private static final Pattern SHIPS_FROM_QUERY_PATTERN = Pattern.compile(
            "(?iu)\\b(?:ship|ships|shipped|shipping)\\s+from\\b|\\bshipping\\s+origin\\b"
                    + "|\\bmade\\s+in\\b|\\borigin(?:ating)?\\s+from\\b"
    );
    private static final Pattern PRICE_QUERY_PATTERN = Pattern.compile(
            "(?iu)(?:\\b(?:under|below|less\\s+than|up\\s+to|over|above|more\\s+than|at\\s+least|between)"
                    + "\\s*\\$?\\s*\\d+(?:\\.\\d{1,2})?"
                    + "(?:\\s*(?:and|-|to)\\s*\\$?\\s*\\d+(?:\\.\\d{1,2})?)?\\b"
                    + "|\\$\\s*\\d+(?:\\.\\d{1,2})?\\b"
                    + "|\\b\\d+(?:\\.\\d{1,2})?\\s*(?:usd|dollars?)\\b)"
    );
    private static final Pattern RATING_QUERY_PATTERN = Pattern.compile(
            "(?iu)\\b(?:at\\s+least\\s+|minimum\\s+|min\\s+)?\\d+(?:\\.\\d+)?\\s*stars?\\b"
                    + "|\\b\\d+\\s*(?:reviews?|ratings?)\\b"
                    + "|\\b(?:rated|rating)\\s+(?:at\\s+least\\s+)?\\d+(?:\\.\\d+)?\\b"
    );
    private static final Pattern GENERIC_INDIFFERENCE_QUERY_PATTERN = Pattern.compile(
            "(?iu)\\b(?:i\\s+)?(?:do\\s+not|don['’]?t)\\s+care\\b"
                    + "|\\bno\\s+preference\\b"
    );
    private static final Pattern CONDITION_INDIFFERENCE_QUERY_PATTERN = indifferenceQueryPattern(
            "condition|new\\s+or\\s+(?:used|secondhand)|(?:used|secondhand)\\s+or\\s+new"
    );
    private static final Pattern SHIPS_TO_INDIFFERENCE_QUERY_PATTERN = indifferenceQueryPattern(
            "shipping\\s+destination|delivery\\s+location|shipping\\s+location|destination|location|country"
    );
    private static final Pattern SHIPS_FROM_INDIFFERENCE_QUERY_PATTERN = indifferenceQueryPattern(
            "shipping\\s+origin|source\\s+country|origin"
    );
    private static final Pattern PRICE_INDIFFERENCE_QUERY_PATTERN = indifferenceQueryPattern(
            "price|budget|cost"
    );
    private static final Pattern COLOR_INDIFFERENCE_QUERY_PATTERN = indifferenceQueryPattern(
            "colou?r"
    );
    private static final Pattern SIZE_INDIFFERENCE_QUERY_PATTERN = indifferenceQueryPattern(
            "(?:(?:product|shoe|clothing)\\s+)?size|sizing"
    );
    private static final Pattern TARGET_GENDER_INDIFFERENCE_QUERY_PATTERN = indifferenceQueryPattern(
            "target\\s+gender|gender"
    );
    private static final Pattern RATING_INDIFFERENCE_QUERY_PATTERN = indifferenceQueryPattern(
            "rating|reviews?|stars?"
    );
    private static final Pattern PRICE_TIER_INDIFFERENCE_QUERY_PATTERN = indifferenceQueryPattern(
            "price\\s+tier|relative\\s+price(?:\\s+tier)?"
    );
    private static final Pattern LOCATION_ANYWHERE_QUERY_PATTERN = Pattern.compile(
            "(?iu)\\b(?:ship(?:ping)?\\s+)?anywhere\\b"
    );
    private static final String PRICE_AMOUNT_PATTERN =
            "(?:\\d{1,3}(?:[\\s.,]\\d{3})+|\\d+)(?:[.,]\\d{1,2})?";
    private static final Pattern PRICE_RANGE_PATTERN = Pattern.compile(
            "(?iu)\\b(?:between|from)\\b[^\\d]{0,24}(?<min>%s)"
                    .formatted(PRICE_AMOUNT_PATTERN)
                    + "\\s+(?:and|to|-)\\s*[^\\d]{0,24}(?<max>%s)"
                    .formatted(PRICE_AMOUNT_PATTERN)
    );
    private static final Pattern PRICE_MAX_PATTERN = Pattern.compile(
            "(?iu)\\b(?:under|below|less\\s+than|up\\s+to|max(?:imum)?|no\\s+more\\s+than)"
                    + "\\b[^\\d]{0,24}(?<amount>%s)".formatted(PRICE_AMOUNT_PATTERN)
    );
    private static final Pattern PRICE_MIN_PATTERN = Pattern.compile(
            "(?iu)\\b(?:over|above|more\\s+than|at\\s+least|min(?:imum)?|no\\s+less\\s+than)"
                    + "\\b[^\\d]{0,24}(?<amount>%s)".formatted(PRICE_AMOUNT_PATTERN)
    );
    private static final Pattern STANDALONE_DENOMINATED_PRICE_PATTERN = Pattern.compile(
            "(?iu)(?:(?<![\\p{L}\\p{N}])\\$\\s*%s"
                    .formatted(PRICE_AMOUNT_PATTERN)
                    + "|\\b(?:usd|u\\.s\\.\\s+dollars?|us\\s+dollars?|dollars?)\\b\\s*%s"
                    .formatted(PRICE_AMOUNT_PATTERN)
                    + "|%s\\s*(?:usd|u\\.s\\.\\s+dollars?|us\\s+dollars?|dollars?)\\b)"
                    .formatted(PRICE_AMOUNT_PATTERN)
    );
    private static final Pattern PRICE_CONTEXT_PATTERN = Pattern.compile(
            "(?iu)(?:\\$|\\b(?:usd|u\\.s\\.\\s+dollars?|us\\s+dollars?|dollars?"
                    + "|price|budget|cost)\\b)"
    );
    private static final String NEW_CONDITION_EXPRESSION =
            "\\b(?:brand\\s+new|new\\s+condition)\\b"
                    + "|\\bnew\\b(?!\\s+(?:balance|era|york|zealand)\\b)";
    private static final Pattern NEW_CONDITION_PATTERN =
            Pattern.compile("(?iu)" + NEW_CONDITION_EXPRESSION);
    private static final Pattern CONDITION_CONSTRAINT_PATTERN = Pattern.compile(
            "(?iu)(?:" + NEW_CONDITION_EXPRESSION
                    + "|\\b(?:used|secondhand|second-hand|preowned|pre-owned)\\b)"
    );
    private static final Pattern COLOR_CONSTRAINT_PATTERN = Pattern.compile(
            "(?iu)\\b(?:colou?r|black|white|red|blue|green|brown|grey|gray|pink|purple|orange|yellow"
                    + "|beige|navy|teal|gold|silver)\\b"
    );
    private static final Pattern SIZE_CONSTRAINT_PATTERN = Pattern.compile(
            "(?iu)\\b(?:size|sizing|xxs|xs|xl|xxl|xxxl)\\b"
                    + "|\\bsize\\s+(?:s|m|l|\\d+(?:[.,]\\d+)?)\\b"
                    + "|\\b(?:s|m|l|\\d+(?:[.,]\\d+)?)\\s+size\\b"
    );
    private static final Pattern TARGET_GENDER_CONSTRAINT_PATTERN = Pattern.compile(
            "(?iu)\\b(?:men['’]?s?|women['’]?s?|male|female|unisex|boys?|girls?|target\\s+gender)\\b"
    );
    private static final Pattern PRICE_TIER_CONSTRAINT_PATTERN = Pattern.compile(
            "(?iu)\\b(?:(?:low|medium|high)\\s+(?:relative\\s+)?price(?:\\s+tier)?"
                    + "|price\\s+tier)\\b"
    );
    private static final Set<String> COLOR_TASTE_TERMS = Set.of(
            "black", "white", "red", "blue", "green", "brown", "grey", "gray", "pink", "purple",
            "orange", "yellow", "beige", "navy", "teal", "gold", "silver"
    );
    private static final Set<String> SIZE_TASTE_TERMS = Set.of(
            "plus", "petite", "tall", "extended", "oversize", "oversized"
    );
    private static final Set<String> TARGET_GENDER_TASTE_TERMS = Set.of(
            "men", "mens", "male", "women", "womens", "female", "unisex", "boy", "boys", "girl", "girls"
    );
    private static final Set<String> FILTER_DECISION_SUBJECT_TERMS = Set.of(
            "budget", "color", "colour", "condition", "cost", "country", "delivery", "destination",
            "filter", "filters", "gender", "include", "keep", "keyword", "leave", "location", "male",
            "female", "men", "mens", "origin", "price", "query", "rating", "retain", "review", "reviews",
            "shipping", "size", "sizing", "star", "stars", "target", "term", "tier", "unisex", "women",
            "womens", "wording"
    );
    private static final Pattern PRODUCT_REQUEST_PATTERN = Pattern.compile(
            "(?iu)\\b(?:buy|find|get|look(?:ing)?\\s+for|search(?:ing)?\\s+for|show\\s+me|switch\\s+to"
                    + "|want)\\b"
    );

    private final UserProductSearchCategoryPolicy categoryPolicy;

    public UserProductSearchQualificationPlanResolver() {
        this(new UserProductSearchCategoryPolicy());
    }

    @Autowired
    public UserProductSearchQualificationPlanResolver(UserProductSearchCategoryPolicy categoryPolicy) {
        this.categoryPolicy = categoryPolicy;
    }

    public Resolution resolve(
            UserProductSearchQualificationPlan candidate,
            GenerateUserProductSearchQualificationQuery query
    ) {
        List<String> violations = new ArrayList<>();
        UserProductSearchQualificationPlan previous = query.previousPlan() != null
                        && query.previousPlan().currentSchema()
                ? query.previousPlan()
                : null;
        String effectiveQuery = candidate.effectiveQuery();

        var condition = unchanged(previous == null ? null : previous.condition(), candidate.condition())
                ? previous.condition()
                : merge(
                        previous == null ? null : previous.condition(),
                        condition(candidate.condition(), query, violations),
                        UserProductSearchQualificationPlan.ConditionFilter::state,
                        UserProductSearchQualificationPlan.ConditionFilter::provenance
                );
        var shipsTo = unchanged(previous == null ? null : previous.shipsTo(), candidate.shipsTo())
                ? previous.shipsTo()
                : merge(
                        previous == null ? null : previous.shipsTo(),
                        shipsTo(candidate.shipsTo(), query, violations),
                        UserProductSearchQualificationPlan.LocationFilter::state,
                        UserProductSearchQualificationPlan.LocationFilter::provenance
                );
        var shipsFrom = unchanged(previous == null ? null : previous.shipsFrom(), candidate.shipsFrom())
                ? previous.shipsFrom()
                : merge(
                        previous == null ? null : previous.shipsFrom(),
                        shipsFrom(candidate.shipsFrom(), query, violations),
                        UserProductSearchQualificationPlan.LocationsFilter::state,
                        UserProductSearchQualificationPlan.LocationsFilter::provenance
                );
        boolean priceUnchanged = unchanged(previous == null ? null : previous.price(), candidate.price());
        var price = priceUnchanged
                ? previous.price()
                : merge(
                        previous == null ? null : previous.price(),
                        price(candidate.price(), query, violations),
                        UserProductSearchQualificationPlan.PriceFilter::state,
                        UserProductSearchQualificationPlan.PriceFilter::provenance
                );
        if (!priceUnchanged
                && price.state() == UserProductSearchFilterState.VALUE
                && !priceBoundsMatchEvidence(price, query)) {
            violations.add("PRICE typed bounds do not match the buyer's grounded bound direction and values");
            price = missingPrice();
        } else if (price.state() == UserProductSearchFilterState.NOT_APPLICABLE
                && hasPriceBound(query)) {
            violations.add("PRICE cannot be irrelevant while a price bound is present");
            price = missingPrice();
        }
        var rating = unchanged(previous == null ? null : previous.rating(), candidate.rating())
                ? previous.rating()
                : merge(
                        previous == null ? null : previous.rating(),
                        rating(candidate.rating(), query, violations),
                        UserProductSearchQualificationPlan.RatingFilter::state,
                        UserProductSearchQualificationPlan.RatingFilter::provenance
                );
        var priceTier = unchanged(previous == null ? null : previous.priceTier(), candidate.priceTier())
                ? previous.priceTier()
                : merge(
                        previous == null ? null : previous.priceTier(),
                        priceTier(candidate.priceTier(), query, violations),
                        UserProductSearchQualificationPlan.PriceTierFilter::state,
                        UserProductSearchQualificationPlan.PriceTierFilter::provenance
                );
        var attributes = attributes(
                candidate.attributes(),
                previous == null ? null : previous.attributes(),
                query,
                violations
        );
        ActiveRequirementResolution activeRequirements = enforceActiveRequestRequirements(
                condition,
                shipsTo,
                shipsFrom,
                price,
                attributes,
                rating,
                priceTier,
                query,
                violations
        );
        condition = activeRequirements.condition();
        shipsTo = activeRequirements.shipsTo();
        shipsFrom = activeRequirements.shipsFrom();
        price = activeRequirements.price();
        attributes = activeRequirements.attributes();
        rating = activeRequirements.rating();
        priceTier = activeRequirements.priceTier();
        EffectiveQueryDecisions queryDecisions = new EffectiveQueryDecisions(
                condition,
                shipsTo,
                shipsFrom,
                price,
                attributes,
                rating,
                priceTier
        );
        effectiveQuery = sanitizeExplicitAnyTerms(effectiveQuery, queryDecisions, query);
        if (!validateEffectiveQuery(effectiveQuery, query, queryDecisions, violations)) {
            effectiveQuery = sanitizeExplicitAnyTerms(
                    query.originalQuery(),
                    queryDecisions,
                    query
            );
        }

        UserProductSearchQualificationPlan resolved = new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                effectiveQuery,
                candidate.assistantMessage(),
                candidate.suggestedReplies(),
                candidate.questionTargets(),
                new UserProductSearchQualificationPlan.AvailableFilter(
                        UserProductSearchFilterState.VALUE,
                        true,
                        UserProductSearchQualificationPlan.Provenance.system("sale-ready products only")
                ),
                condition,
                shipsTo,
                shipsFrom,
                price,
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.system("trusted shop resolver unavailable")
                ),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.system("trusted taxonomy resolver unavailable")
                ),
                attributes,
                rating,
                priceTier,
                unchanged(
                        previous == null ? null : previous.durableAttributes(),
                        candidate.durableAttributes()
                )
                        ? previous.durableAttributes()
                        : durableAttributes(
                                candidate.durableAttributes(),
                                candidate.attributes(),
                                attributes,
                                query,
                                violations
                        )
        );
        resolved = categoryPolicy.enforce(resolved, query);
        validateQuestionCoverage(resolved, violations);
        return new Resolution(resolved, List.copyOf(violations));
    }

    private boolean validateEffectiveQuery(
            String effectiveQuery,
            GenerateUserProductSearchQualificationQuery query,
            EffectiveQueryDecisions decisions,
            List<String> violations
    ) {
        int initialViolationCount = violations.size();
        if (effectiveQuery.length() > UserProductSearchQueryLimits.MAX_SEARCH_QUERY_LENGTH) {
            violations.add("effectiveQuery exceeds the downstream product-search query limit");
        }
        Set<String> userTokens = new LinkedHashSet<>();
        addTokens(userTokens, query.originalQuery());
        if (currentTurnBelongsToActiveProduct(query)) {
            addTokens(userTokens, query.message());
        }
        activeUserConversationMessages(query)
                .forEach(message -> addTokens(userTokens, message.text()));
        Set<String> trustedTokens = new LinkedHashSet<>(userTokens);
        addTokens(trustedTokens, query.settings().clothingFit());
        safe(query.settings().filters()).forEach(filter -> {
            addTokens(trustedTokens, filter.label());
            addTokens(trustedTokens, filter.description());
        });
        query.durablePreferences().forEach(preference -> {
            addTokens(trustedTokens, preference.scope());
            preference.values().forEach(value -> addTokens(trustedTokens, value));
        });
        query.tasteProfile().signals().stream()
                .filter(signal -> signal.status() == UserTasteSignalStatus.ACTIVE)
                .forEach(signal -> addTokens(trustedTokens, signal.label()));

        List<String> effectiveTokens = tokens(effectiveQuery);
        List<String> unsupported = effectiveTokens.stream()
                .filter(token -> !EFFECTIVE_QUERY_CONNECTORS.contains(token))
                .filter(token -> trustedTokens.stream().noneMatch(trusted -> sameWord(token, trusted)))
                .distinct()
                .toList();
        if (!unsupported.isEmpty()) {
            violations.add("effectiveQuery contains unsupported terms: " + unsupported);
        }
        validateExplicitAnyDoesNotRestoreSavedTerms(
                effectiveQuery,
                effectiveTokens,
                decisions,
                query,
                violations
        );

        String semanticOriginalQuery = sanitizeExplicitAnyTerms(
                query.originalQuery(),
                decisions,
                query
        );
        var originalSubject = categoryPolicy.productSubject(semanticOriginalQuery);
        var effectiveSubject = categoryPolicy.productSubject(effectiveQuery);
        boolean retainsOriginalSubject = originalSubject.isPresent()
                && effectiveSubject.isPresent()
                && sameWord(originalSubject.get().head(), effectiveSubject.get().head());
        if (!retainsOriginalSubject) {
            violations.add("effectiveQuery must retain a product term from the original request");
        }
        return violations.size() == initialViolationCount;
    }

    private void validateExplicitAnyDoesNotRestoreSavedTerms(
            String effectiveQuery,
            List<String> effectiveTokens,
            EffectiveQueryDecisions decisions,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        for (UserProductSearchQuestionTarget target : explicitAnyTargets(decisions)) {
            Set<String> overriddenTokens = overriddenQueryTokens(target, query);
            List<String> restored = effectiveTokens.stream()
                    .filter(token -> overriddenTokens.stream()
                            .anyMatch(overridden -> sameWord(token, overridden)))
                    .distinct()
                    .toList();
            boolean retainedSyntax = explicitAnySyntaxPatterns(target).stream()
                    .anyMatch(pattern -> pattern.matcher(effectiveQuery).find());
            if (!restored.isEmpty() || retainedSyntax) {
                violations.add("effectiveQuery reintroduces overridden " + target
                        + " terms after explicit ANY: " + restored);
            }
        }
    }

    private boolean isExplicitAny(
            UserProductSearchFilterState state,
            UserProductSearchQualificationPlan.Provenance provenance
    ) {
        if (state != UserProductSearchFilterState.ANY
                || provenance == null
                || normalize(provenance.evidence()).isBlank()) {
            return false;
        }
        return switch (provenance.source()) {
            case ORIGINAL_QUERY, CURRENT_USER_TURN, CONVERSATION -> true;
            case PROFILE, DURABLE_PREFERENCE, NONE, SYSTEM_POLICY -> false;
        };
    }

    private boolean isExplicitAny(UserProductSearchQualificationPlan.Attribute attribute) {
        return isExplicitAny(attribute.state(), attribute.provenance());
    }

    private String sanitizeExplicitAnyTerms(
            String value,
            EffectiveQueryDecisions decisions,
            GenerateUserProductSearchQualificationQuery query
    ) {
        Set<UserProductSearchQuestionTarget> explicitAnyTargets = explicitAnyTargets(decisions);
        String stripped = value.trim();
        for (UserProductSearchQuestionTarget target : explicitAnyTargets) {
            for (Pattern pattern : explicitAnySyntaxPatterns(target)) {
                stripped = pattern.matcher(stripped).replaceAll(" ");
            }
        }

        Set<String> overriddenTokens = new LinkedHashSet<>();
        explicitAnyTargets.forEach(target ->
                overriddenTokens.addAll(overriddenQueryTokens(target, query)));
        if (overriddenTokens.isEmpty()) {
            return stripped.equals(value.trim()) ? value.trim() : String.join(" ", tokens(stripped));
        }

        List<String> source = tokens(stripped);
        List<String> sanitized = new ArrayList<>();
        boolean removed = !stripped.equals(value.trim());
        for (int index = 0; index < source.size(); index++) {
            String token = source.get(index);
            if (overriddenTokens.stream().noneMatch(overridden -> sameWord(token, overridden))) {
                sanitized.add(token);
                continue;
            }
            removed = true;
            if (index + 1 < source.size() && source.get(index + 1).equals("s")) {
                index++;
            }
        }
        return removed ? String.join(" ", sanitized).trim() : value.trim();
    }

    private Set<UserProductSearchQuestionTarget> explicitAnyTargets(EffectiveQueryDecisions decisions) {
        Set<UserProductSearchQuestionTarget> targets =
                java.util.EnumSet.noneOf(UserProductSearchQuestionTarget.class);
        if (isExplicitAny(decisions.condition().state(), decisions.condition().provenance())) {
            targets.add(UserProductSearchQuestionTarget.CONDITION);
        }
        if (isExplicitAny(decisions.shipsTo().state(), decisions.shipsTo().provenance())) {
            targets.add(UserProductSearchQuestionTarget.SHIPS_TO);
        }
        if (isExplicitAny(decisions.shipsFrom().state(), decisions.shipsFrom().provenance())) {
            targets.add(UserProductSearchQuestionTarget.SHIPS_FROM);
        }
        if (isExplicitAny(decisions.price().state(), decisions.price().provenance())) {
            targets.add(UserProductSearchQuestionTarget.PRICE);
        }
        decisions.attributes().values().stream()
                .filter(this::isExplicitAny)
                .map(attribute -> target(attribute.name()))
                .forEach(targets::add);
        if (isExplicitAny(decisions.rating().state(), decisions.rating().provenance())) {
            targets.add(UserProductSearchQuestionTarget.RATING);
        }
        if (isExplicitAny(decisions.priceTier().state(), decisions.priceTier().provenance())) {
            targets.add(UserProductSearchQuestionTarget.PRICE_TIER);
        }
        return targets;
    }

    private List<Pattern> explicitAnySyntaxPatterns(UserProductSearchQuestionTarget target) {
        return switch (target) {
            case CONDITION -> List.of(
                    CONDITION_INDIFFERENCE_QUERY_PATTERN,
                    GENERIC_INDIFFERENCE_QUERY_PATTERN
            );
            case SHIPS_TO -> List.of(
                    SHIPS_TO_INDIFFERENCE_QUERY_PATTERN,
                    GENERIC_INDIFFERENCE_QUERY_PATTERN,
                    LOCATION_ANYWHERE_QUERY_PATTERN,
                    SHIPS_TO_QUERY_PATTERN
            );
            case SHIPS_FROM -> List.of(
                    SHIPS_FROM_INDIFFERENCE_QUERY_PATTERN,
                    GENERIC_INDIFFERENCE_QUERY_PATTERN,
                    LOCATION_ANYWHERE_QUERY_PATTERN,
                    SHIPS_FROM_QUERY_PATTERN
            );
            case PRICE -> List.of(
                    PRICE_INDIFFERENCE_QUERY_PATTERN,
                    GENERIC_INDIFFERENCE_QUERY_PATTERN,
                    PRICE_QUERY_PATTERN
            );
            case COLOR -> List.of(
                    COLOR_INDIFFERENCE_QUERY_PATTERN,
                    GENERIC_INDIFFERENCE_QUERY_PATTERN
            );
            case SIZE -> List.of(
                    SIZE_INDIFFERENCE_QUERY_PATTERN,
                    GENERIC_INDIFFERENCE_QUERY_PATTERN
            );
            case TARGET_GENDER -> List.of(
                    TARGET_GENDER_INDIFFERENCE_QUERY_PATTERN,
                    GENERIC_INDIFFERENCE_QUERY_PATTERN
            );
            case RATING -> List.of(
                    RATING_INDIFFERENCE_QUERY_PATTERN,
                    GENERIC_INDIFFERENCE_QUERY_PATTERN,
                    RATING_QUERY_PATTERN
            );
            case PRICE_TIER -> List.of(
                    PRICE_TIER_INDIFFERENCE_QUERY_PATTERN,
                    GENERIC_INDIFFERENCE_QUERY_PATTERN
            );
        };
    }

    private static Pattern indifferenceQueryPattern(String targetExpression) {
        String target = "(?:" + targetExpression + ")";
        String indifference = "(?:doesn['’]?t\\s+matter|doesnt\\s+matter|does\\s+not\\s+matter"
                + "|is\\s+irrelevant|is\\s+not\\s+important|can\\s+be\\s+anything"
                + "|any|whatever)";
        return Pattern.compile(
                "(?iu)\\b(?:"
                        + "(?:any|whatever)\\s+(?:the\\s+)?" + target
                        + "|(?:the\\s+)?" + target + "\\s+" + indifference
                        + "|no\\s+" + target + "\\s+preference"
                        + "|no\\s+preference\\s+(?:for|on)\\s+(?:the\\s+)?" + target
                        + "|(?:i\\s+)?(?:do\\s+not|don['’]?t)\\s+care\\s+"
                        + "(?:about\\s+)?(?:the\\s+)?" + target
                        + ")\\b"
        );
    }

    private Set<String> overriddenQueryTokens(
            UserProductSearchQuestionTarget target,
            GenerateUserProductSearchQualificationQuery query
    ) {
        Set<String> overriddenTokens = new LinkedHashSet<>();
        Set<String> explicitlyKeptTokens = new LinkedHashSet<>();
        overriddenQueryValues(target, query).forEach(value -> {
            if (latestTurnExplicitlyKeepsValue(query.message(), value)) {
                addTokens(explicitlyKeptTokens, value);
            } else {
                addTokens(overriddenTokens, value);
            }
        });
        overriddenTokens.removeIf(overridden -> explicitlyKeptTokens.stream()
                .anyMatch(kept -> sameWord(overridden, kept)));
        return overriddenTokens;
    }

    private List<String> overriddenQueryValues(
            UserProductSearchQuestionTarget target,
            GenerateUserProductSearchQualificationQuery query
    ) {
        return switch (target) {
            case COLOR -> overriddenAttributeValues(UserProductSearchAttributeName.COLOR, query);
            case SIZE -> overriddenAttributeValues(UserProductSearchAttributeName.SIZE, query);
            case TARGET_GENDER ->
                    overriddenAttributeValues(UserProductSearchAttributeName.TARGET_GENDER, query);
            case CONDITION -> overriddenConditionValues(query);
            case SHIPS_TO -> overriddenLocationValues(query, true);
            case SHIPS_FROM -> overriddenLocationValues(query, false);
            case PRICE -> overriddenPriceValues(query);
            case RATING -> overriddenRatingValues(query);
            case PRICE_TIER -> overriddenPriceTierValues(query);
        };
    }

    private List<String> overriddenAttributeValues(
            UserProductSearchAttributeName attributeName,
            GenerateUserProductSearchQualificationQuery query
    ) {
        Set<String> values = new LinkedHashSet<>();
        if (attributeName == UserProductSearchAttributeName.TARGET_GENDER) {
            values.addAll(TARGET_GENDER_TASTE_TERMS);
        }
        query.durablePreferences().stream()
                .filter(preference -> preference.attributeName() == attributeName)
                .flatMap(preference -> preference.values().stream())
                .filter(value -> value != null && !value.isBlank())
                .forEach(values::add);
        if (attributeName == UserProductSearchAttributeName.TARGET_GENDER) {
            String clothingFit = query.settings().clothingFit();
            if (clothingFit != null && !clothingFit.isBlank()) {
                values.add(clothingFit);
            }
        }
        activeTasteAttributeValues(attributeName, query).forEach(values::add);
        UserProductSearchQualificationPlan previous = query.previousPlan();
        if (previous != null && previous.currentSchema()) {
            previous.attributes().values().stream()
                    .filter(attribute -> attribute.name() == attributeName)
                    .flatMap(attribute -> attribute.values().stream())
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(values::add);
        }
        return List.copyOf(values);
    }

    private List<String> activeTasteAttributeValues(
            UserProductSearchAttributeName attributeName,
            GenerateUserProductSearchQualificationQuery query
    ) {
        Set<String> terms = switch (attributeName) {
            case COLOR -> COLOR_TASTE_TERMS;
            case SIZE -> SIZE_TASTE_TERMS;
            case TARGET_GENDER -> TARGET_GENDER_TASTE_TERMS;
        };
        Set<String> values = new LinkedHashSet<>();
        List<String> labels = query.tasteProfile().signals().stream()
                .filter(signal -> signal.status() == UserTasteSignalStatus.ACTIVE)
                .map(signal -> normalize(signal.label()))
                .toList();
        labels.stream()
                .flatMap(label -> java.util.Arrays.stream(label.split(" ")))
                .filter(terms::contains)
                .forEach(values::add);
        if (attributeName == UserProductSearchAttributeName.SIZE
                && labels.stream().anyMatch(label -> containsPhrase(label, "plus size")
                        || containsPhrase(label, "extended size"))) {
            values.add("size");
        }
        return List.copyOf(values);
    }

    private List<String> overriddenConditionValues(GenerateUserProductSearchQualificationQuery query) {
        Set<String> values = new LinkedHashSet<>(List.of(
                "new", "used", "secondhand", "second hand", "preowned", "pre owned", "condition"
        ));
        UserProductSearchQualificationPlan previous = currentPreviousPlan(query);
        if (previous != null) {
            previous.condition().values().forEach(value -> values.add(value.name()));
        }
        return List.copyOf(values);
    }

    private List<String> overriddenLocationValues(
            GenerateUserProductSearchQualificationQuery query,
            boolean shipsTo
    ) {
        Set<String> values = new LinkedHashSet<>();
        if (shipsTo) {
            addLocationValues(values, query.settings().location());
        }
        UserProductSearchQualificationPlan previous = currentPreviousPlan(query);
        if (previous != null) {
            if (shipsTo && previous.shipsTo().value() != null) {
                addLocationValues(values, previous.shipsTo().value());
            } else if (!shipsTo) {
                previous.shipsFrom().values().forEach(location -> addLocationValues(values, location));
            }
        }
        addMentionedCountryValues(values, query.originalQuery());
        if (previous != null) {
            addMentionedCountryValues(values, previous.effectiveQuery());
        }
        return List.copyOf(values);
    }

    private void addLocationValues(Set<String> values, UserLocationResult location) {
        if (location == null) {
            return;
        }
        addNonBlank(values, location.country());
        addCountryCodeValues(values, location.code());
        addNonBlank(values, location.region());
        addNonBlank(values, location.postalCode());
        addNonBlank(values, location.regionName());
        addNonBlank(values, location.city());
    }

    private void addLocationValues(
            Set<String> values,
            UserProductSearchQualificationPlan.Location location
    ) {
        addCountryCodeValues(values, location.country());
        addNonBlank(values, location.region());
        addNonBlank(values, location.postalCode());
    }

    private void addCountryCodeValues(Set<String> values, String country) {
        addNonBlank(values, country);
        String code = CountryCodeNormalizer.normalizeAlpha2(country);
        if (code == null) {
            return;
        }
        addNonBlank(values, code);
        addNonBlank(values, new Locale.Builder()
                .setRegion(code)
                .build()
                .getDisplayCountry(Locale.ENGLISH));
        if (code.equals("US")) {
            values.add("USA");
        } else if (code.equals("GB")) {
            values.add("UK");
            values.add("Great Britain");
        }
    }

    private void addMentionedCountryValues(Set<String> values, String source) {
        String normalizedSource = normalize(source);
        for (String code : Locale.getISOCountries()) {
            String country = new Locale.Builder()
                    .setRegion(code)
                    .build()
                    .getDisplayCountry(Locale.ENGLISH);
            if (containsPhrase(normalizedSource, normalize(country))
                    || containsUppercaseCode(source, code)) {
                addCountryCodeValues(values, code);
            }
        }
        if (containsPhrase(normalizedSource, "usa")) {
            addCountryCodeValues(values, "US");
        }
        if (containsPhrase(normalizedSource, "uk")
                || containsPhrase(normalizedSource, "great britain")) {
            addCountryCodeValues(values, "GB");
        }
    }

    private List<String> overriddenPriceValues(GenerateUserProductSearchQualificationQuery query) {
        Set<String> values = new LinkedHashSet<>(List.of(
                "price", "budget", "cost", "usd", "dollar", "dollars"
        ));
        UserProductSearchQualificationPlan previous = currentPreviousPlan(query);
        if (previous != null) {
            if (previous.price().minUsdMinor() != null) {
                values.add(majorAmount(previous.price().minUsdMinor(), preferredCurrency(query)));
            }
            if (previous.price().maxUsdMinor() != null) {
                values.add(majorAmount(previous.price().maxUsdMinor(), preferredCurrency(query)));
            }
        }
        return List.copyOf(values);
    }

    private List<String> overriddenRatingValues(GenerateUserProductSearchQualificationQuery query) {
        Set<String> values = new LinkedHashSet<>(List.of(
                "rating", "rated", "star", "stars", "review", "reviews"
        ));
        UserProductSearchQualificationPlan previous = currentPreviousPlan(query);
        if (previous != null) {
            if (previous.rating().min() != null) {
                values.add(previous.rating().min().stripTrailingZeros().toPlainString());
            }
            if (previous.rating().minCount() != null) {
                values.add(previous.rating().minCount().toString());
            }
        }
        return List.copyOf(values);
    }

    private List<String> overriddenPriceTierValues(GenerateUserProductSearchQualificationQuery query) {
        Set<String> values = new LinkedHashSet<>(List.of(
                "price tier", "relative price", "low", "cheap", "budget", "affordable",
                "medium", "mid range", "midrange", "high", "premium", "luxury"
        ));
        UserProductSearchQualificationPlan previous = currentPreviousPlan(query);
        if (previous != null) {
            previous.priceTier().values().forEach(value -> {
                values.add(value.name());
                switch (value) {
                    case LOW -> values.addAll(List.of("cheap", "budget", "affordable"));
                    case MEDIUM -> values.addAll(List.of("mid range", "midrange"));
                    case HIGH -> values.addAll(List.of("premium", "luxury"));
                }
            });
        }
        return List.copyOf(values);
    }

    private UserProductSearchQualificationPlan currentPreviousPlan(
            GenerateUserProductSearchQualificationQuery query
    ) {
        return query.previousPlan() != null && query.previousPlan().currentSchema()
                ? query.previousPlan()
                : null;
    }

    private void addNonBlank(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private boolean latestTurnExplicitlyKeepsValue(String turn, String value) {
        String normalizedTurn = normalize(turn);
        String normalizedValue = normalize(value);
        if (normalizedTurn.isBlank() || normalizedValue.isBlank()) {
            return false;
        }
        String valuePattern = Pattern.quote(normalizedValue) + "(?:\\s+s)?";
        String instruction = "\\b(?:keep|retain|include|leave)\\s+(?:the\\s+)?"
                + valuePattern
                + "\\s+(?:(?:in|inside)\\s+(?:the\\s+)?(?:product\\s+|search\\s+)?"
                + "(?:query|search)|as\\s+(?:a\\s+)?(?:query\\s+|search\\s+)?"
                + "(?:term|keyword|wording))\\b";
        return Pattern.compile(instruction).matcher(normalizedTurn).find();
    }

    private void addTokens(Set<String> destination, String value) {
        destination.addAll(tokens(value));
    }

    private List<String> tokens(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? List.of() : List.of(normalized.split(" "));
    }

    private boolean sameWord(String left, String right) {
        return wordStem(left).equals(wordStem(right));
    }

    private String wordStem(String value) {
        if (value.endsWith("ies") && value.length() > 4) {
            return value.substring(0, value.length() - 3) + "y";
        }
        if (value.endsWith("s") && !value.endsWith("ss") && value.length() > 3) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    public UserProductSearchQualificationPlan safeFallback(UserProductSearchQualificationPlan plan) {
        return safeFallback(plan, UserCurrency.DEFAULT);
    }

    private UserProductSearchQualificationPlan safeFallback(
            UserProductSearchQualificationPlan plan,
            String preferredCurrency
    ) {
        requireSafeFallbackQuery(plan.effectiveQuery());
        List<UserProductSearchQuestionTarget> missing = plan.missingTargets();
        if (missing.isEmpty()) {
            return plan.withConversation("I have everything I need to search.", List.of(), List.of());
        }
        if (missing.size() == 2
                && missing.contains(UserProductSearchQuestionTarget.SIZE)
                && missing.contains(UserProductSearchQuestionTarget.SHIPS_TO)) {
            return plan.withConversation(
                    "What product size do you need, and what country should it ship to? "
                            + "You may also include a region or postal code. "
                            + "You can answer either one, say which one does not matter, or say “I don’t care” "
                            + "if neither should filter the search.",
                    List.of(),
                    missing
            );
        }
        if (missing.size() == 1 && missing.getFirst() == UserProductSearchQuestionTarget.PRICE) {
            return priceFallbackQuestion(plan, missing, preferredCurrency);
        }
        String labels = missing.stream()
                .map(target -> label(target, preferredCurrency))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return plan.withConversation(
                "I still need your preferences for " + labels + ". Please answer each one, or say explicitly "
                        + "which ones do not matter to you.",
                List.of(),
                missing
        );
    }

    public UserProductSearchQualificationPlan safeFallback(
            UserProductSearchQualificationPlan plan,
            GenerateUserProductSearchQualificationQuery query
    ) {
        UserProductSearchQualificationPlan continuation =
                conservativeContinuation(query).orElse(plan);
        return safeFallback(categoryPolicy.enforceConservativeFallback(continuation, query), preferredCurrency(query));
    }

    public UserProductSearchQualificationPlan safeFallback(GenerateUserProductSearchQualificationQuery query) {
        return safeFallback(query, null);
    }

    public UserProductSearchQualificationPlan safeFallback(
            GenerateUserProductSearchQualificationQuery query,
            Throwable cause
    ) {
        Optional<UserProductSearchQualificationPlan> continuation = conservativeContinuation(query);
        if (continuation.isPresent()) {
            return safeFallback(categoryPolicy.enforceConservativeFallback(
                    continuation.get(),
                    query
            ), preferredCurrency(query));
        }
        String denialReason = categoryPolicy.conservativeFallbackDenialReason(query);
        if (denialReason != null) {
            String message = "Product-search qualification failed and no conservative category fallback was available"
                    + " (reason=" + denialReason + ")";
            if (cause == null) {
                throw new OpenRouterException(message);
            }
            throw new OpenRouterException(message, cause);
        }
        UserProductSearchQualificationPlan.Provenance none = UserProductSearchQualificationPlan.Provenance.none();
        List<UserProductSearchQualificationPlan.Attribute> attributes = java.util.Arrays.stream(
                        UserProductSearchAttributeName.values())
                .map(name -> new UserProductSearchQualificationPlan.Attribute(
                        name, UserProductSearchFilterState.NOT_APPLICABLE, List.of(), none))
                .toList();
        UserProductSearchQualificationPlan fallback = new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                query.originalQuery().trim(),
                "I can start with a broad search and refine from the results.",
                List.of(),
                List.of(),
                new UserProductSearchQualificationPlan.AvailableFilter(
                        UserProductSearchFilterState.VALUE,
                        true,
                        UserProductSearchQualificationPlan.Provenance.system("sale-ready products only")
                ),
                new UserProductSearchQualificationPlan.ConditionFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(), none),
                new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, none),
                new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(), none),
                new UserProductSearchQualificationPlan.PriceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, null, none),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.system("trusted shop resolver unavailable")
                ),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.system("trusted taxonomy resolver unavailable")
                ),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, attributes),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, null, none),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(), none),
                List.of()
        );
        return safeFallback(categoryPolicy.enforceConservativeFallback(fallback, query), preferredCurrency(query));
    }

    private void requireSafeFallbackQuery(String effectiveQuery) {
        if (effectiveQuery != null
                && effectiveQuery.length() > UserProductSearchQueryLimits.MAX_SEARCH_QUERY_LENGTH) {
            throw new OpenRouterException(
                    "Product-search qualification fallback cannot safely preserve a request longer than "
                            + UserProductSearchQueryLimits.MAX_SEARCH_QUERY_LENGTH
                            + " characters; please restate the product request more concisely"
            );
        }
    }

    private UserProductSearchQualificationPlan priceFallbackQuestion(
            UserProductSearchQualificationPlan plan,
            List<UserProductSearchQuestionTarget> missing,
            String preferredCurrency
    ) {
        String query = plan.effectiveQuery();
        boolean directionalBound = directionalPriceBoundMentioned(query);
        boolean standaloneAmount = STANDALONE_DENOMINATED_PRICE_PATTERN.matcher(query).find()
                && !directionalBound;
        if (standaloneAmount) {
            return plan.withConversation(
                    "Should the stated " + preferredCurrency
                            + " amount be a minimum, a maximum, or one end of a range?",
                    List.of(),
                    missing
            );
        }
        return plan.withConversation(
                directionalBound
                        ? "Please confirm the price bound I should use in " + preferredCurrency + "."
                        : "What minimum, maximum, or price range should I use? "
                        + "Your Account settings currently use " + preferredCurrency + ". "
                        + "You can also say that price does not matter.",
                List.of("Price does not matter"),
                missing
        );
    }

    private Optional<UserProductSearchQualificationPlan> conservativeContinuation(
            GenerateUserProductSearchQualificationQuery query
    ) {
        UserProductSearchQualificationPlan previous = query.previousPlan();
        if (previous == null
                || !previous.currentSchema()
                || previous.questionTargets().isEmpty()) {
            return Optional.empty();
        }
        String turn = query.message().trim();
        EnumMap<UserProductSearchQuestionTarget, DirectAnswer> answers =
                new EnumMap<>(UserProductSearchQuestionTarget.class);
        Set<UserProductSearchQuestionTarget> conflicts =
                java.util.EnumSet.noneOf(UserProductSearchQuestionTarget.class);
        List<String> clauses = answerClauses(turn);
        for (String clause : clauses) {
            if (!containsIndifference(clause)) {
                continue;
            }
            previous.questionTargets().stream()
                    .filter(target -> mentionsTarget(clause, target))
                    .forEach(target -> addDirectAnswer(
                            answers, conflicts, target, DirectAnswer.any()));
        }

        for (String clause : clauses) {
            directSizeValue(clause)
                    .filter(ignored -> previous.questionTargets()
                            .contains(UserProductSearchQuestionTarget.SIZE))
                    .ifPresent(value -> addDirectAnswer(
                            answers,
                            conflicts,
                            UserProductSearchQuestionTarget.SIZE,
                            DirectAnswer.value(value)
                    ));
            List<UserProductSearchQuestionTarget> locationTargets = previous.questionTargets().stream()
                    .filter(target -> target == UserProductSearchQuestionTarget.SHIPS_TO
                            || target == UserProductSearchQuestionTarget.SHIPS_FROM)
                    .toList();
            if (locationTargets.size() == 1) {
                directCountryCode(clause).ifPresent(country -> addDirectAnswer(
                        answers,
                        conflicts,
                        locationTargets.getFirst(),
                        DirectAnswer.value(country)
                ));
            }
        }
        boolean namesAskedTarget = previous.questionTargets().stream()
                .anyMatch(target -> mentionsTarget(turn, target));
        if (answers.isEmpty() && conflicts.isEmpty()
                && containsIndifference(turn)
                && !namesAskedTarget) {
            previous.questionTargets().forEach(target -> answers.put(target, DirectAnswer.any()));
        }
        if (answers.isEmpty()) {
            return Optional.empty();
        }

        UserProductSearchQualificationPlan candidate =
                applyDirectAnswers(previous, turn, answers, preferredCurrency(query));
        Resolution resolution = resolve(candidate, query);
        return resolution.valid() ? Optional.of(resolution.plan()) : Optional.empty();
    }

    private List<String> answerClauses(String turn) {
        return java.util.Arrays.stream(turn.split("(?i)\\s*(?:[,;]|\\b(?:and|but|then|while)\\b)\\s*"))
                .map(String::trim)
                .filter(clause -> !clause.isBlank())
                .toList();
    }

    private void addDirectAnswer(
            EnumMap<UserProductSearchQuestionTarget, DirectAnswer> answers,
            Set<UserProductSearchQuestionTarget> conflicts,
            UserProductSearchQuestionTarget target,
            DirectAnswer answer
    ) {
        if (conflicts.contains(target)) {
            return;
        }
        DirectAnswer existing = answers.get(target);
        if (existing != null && !existing.equals(answer)) {
            answers.remove(target);
            conflicts.add(target);
            return;
        }
        answers.put(target, answer);
    }

    private UserProductSearchQualificationPlan applyDirectAnswers(
            UserProductSearchQualificationPlan previous,
            String turn,
            EnumMap<UserProductSearchQuestionTarget, DirectAnswer> answers,
            String preferredCurrency
    ) {
        UserProductSearchQualificationPlan.Provenance current =
                new UserProductSearchQualificationPlan.Provenance(
                        UserProductSearchDecisionSource.CURRENT_USER_TURN,
                        turn
                );
        var condition = previous.condition();
        var shipsTo = previous.shipsTo();
        var shipsFrom = previous.shipsFrom();
        var price = previous.price();
        var rating = previous.rating();
        var priceTier = previous.priceTier();
        EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> attributes =
                byAttributeName(previous.attributes().values());

        for (var entry : answers.entrySet()) {
            DirectAnswer answer = entry.getValue();
            UserProductSearchFilterState state = answer.explicitAny()
                    ? UserProductSearchFilterState.ANY
                    : UserProductSearchFilterState.VALUE;
            switch (entry.getKey()) {
                case CONDITION -> condition = new UserProductSearchQualificationPlan.ConditionFilter(
                        state,
                        List.of(),
                        current
                );
                case SHIPS_TO -> shipsTo = new UserProductSearchQualificationPlan.LocationFilter(
                        state,
                        answer.explicitAny()
                                ? null
                                : new UserProductSearchQualificationPlan.Location(
                                        answer.value(), null, null),
                        current
                );
                case SHIPS_FROM -> shipsFrom = new UserProductSearchQualificationPlan.LocationsFilter(
                        state,
                        answer.explicitAny()
                                ? List.of()
                                : List.of(new UserProductSearchQualificationPlan.Location(
                                        answer.value(), null, null)),
                        current
                );
                case PRICE -> price = new UserProductSearchQualificationPlan.PriceFilter(
                        state,
                        null,
                        null,
                        current
                );
                case COLOR, SIZE, TARGET_GENDER -> {
                    UserProductSearchAttributeName name = switch (entry.getKey()) {
                        case COLOR -> UserProductSearchAttributeName.COLOR;
                        case SIZE -> UserProductSearchAttributeName.SIZE;
                        case TARGET_GENDER -> UserProductSearchAttributeName.TARGET_GENDER;
                        default -> throw new IllegalStateException("Unexpected attribute target");
                    };
                    attributes.put(name, new UserProductSearchQualificationPlan.Attribute(
                            name,
                            state,
                            answer.explicitAny() ? List.of() : List.of(answer.value()),
                            current
                    ));
                }
                case RATING -> rating = new UserProductSearchQualificationPlan.RatingFilter(
                        state,
                        null,
                        null,
                        current
                );
                case PRICE_TIER -> priceTier =
                        new UserProductSearchQualificationPlan.PriceTierFilter(
                                state,
                                List.of(),
                                current
                        );
            }
        }

        List<UserProductSearchQualificationPlan.Attribute> attributeValues =
                List.copyOf(attributes.values());
        UserProductSearchFilterState attributeState = attributeValues.stream()
                .anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.MISSING)
                ? UserProductSearchFilterState.MISSING
                : attributeValues.stream()
                        .anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.VALUE)
                ? UserProductSearchFilterState.VALUE
                : attributeValues.stream()
                        .anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.ANY)
                ? UserProductSearchFilterState.ANY
                : UserProductSearchFilterState.NOT_APPLICABLE;
        List<UserProductSearchQuestionTarget> remaining = previous.questionTargets().stream()
                .filter(target -> !answers.containsKey(target))
                .toList();
        String assistantMessage = remaining.isEmpty()
                ? "I have everything I need to search."
                : "Please provide " + remaining.stream()
                        .map(target -> label(target, preferredCurrency))
                        .reduce((left, right) -> left + ", " + right)
                        .orElseThrow() + " before I search.";

        return new UserProductSearchQualificationPlan(
                previous.schemaVersion(),
                previous.effectiveQuery(),
                assistantMessage,
                List.of(),
                remaining,
                previous.available(),
                condition,
                shipsTo,
                shipsFrom,
                price,
                previous.shops(),
                previous.categories(),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        attributeState,
                        attributeValues
                ),
                rating,
                priceTier,
                List.of()
        );
    }

    private Optional<String> directSizeValue(String turn) {
        String value = turn.trim();
        if (value.regionMatches(true, 0, "size ", 0, 5)) {
            value = value.substring(5).trim();
        }
        if (!value.matches(
                "(?i)(?:x{0,4}[sl]|m|small|medium|large|extra[- ]?(?:small|large)|"
                        + "one[- ]?size|os|osfa|\\d+x[sl]|(?:uk|us|eu)\\s*\\d{1,3}(?:\\.5)?|"
                        + "[a-z]?\\d{1,3}(?:[./-]\\d{1,3})?[a-z]?|w\\d{1,3}(?:\\s*l\\d{1,3})?)")) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private Optional<String> directCountryCode(String turn) {
        String value = normalize(turn);
        for (String prefix : List.of(
                "i am in ", "i m in ", "im in ", "i live in ", "based in ", "located in ",
                "ship to ", "ships to ", "deliver to ", "delivery to "
        )) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length()).trim();
                break;
            }
        }
        String alpha2 = CountryCodeNormalizer.normalizeAlpha2(value);
        if (alpha2 != null) {
            return Optional.of(alpha2);
        }
        String alias = switch (value) {
            case "usa", "united states of america" -> "US";
            case "uk", "great britain" -> "GB";
            default -> null;
        };
        if (alias != null) {
            return Optional.of(alias);
        }
        for (String countryCode : Locale.getISOCountries()) {
            String country = new Locale.Builder()
                    .setRegion(countryCode)
                    .build()
                    .getDisplayCountry(Locale.ENGLISH);
            if (normalize(country).equals(value)) {
                return Optional.of(countryCode);
            }
        }
        return Optional.empty();
    }

    private record DirectAnswer(boolean explicitAny, String value) {

        private static DirectAnswer any() {
            return new DirectAnswer(true, null);
        }

        private static DirectAnswer value(String value) {
            return new DirectAnswer(false, value);
        }
    }

    private record EffectiveQueryDecisions(
            UserProductSearchQualificationPlan.ConditionFilter condition,
            UserProductSearchQualificationPlan.LocationFilter shipsTo,
            UserProductSearchQualificationPlan.LocationsFilter shipsFrom,
            UserProductSearchQualificationPlan.PriceFilter price,
            UserProductSearchQualificationPlan.AttributesFilter attributes,
            UserProductSearchQualificationPlan.RatingFilter rating,
            UserProductSearchQualificationPlan.PriceTierFilter priceTier
    ) {
    }

    private enum ActiveRequirementKind {
        NONE,
        VALUE,
        ANY
    }

    private record ActiveTargetRequirement(
            ActiveRequirementKind kind,
            UserProductSearchDecisionSource source,
            String evidence
    ) {

        private static ActiveTargetRequirement none() {
            return new ActiveTargetRequirement(ActiveRequirementKind.NONE, null, null);
        }

        private UserProductSearchQualificationPlan.Provenance provenance() {
            return new UserProductSearchQualificationPlan.Provenance(source, evidence);
        }
    }

    private record ActiveRequirementResolution(
            UserProductSearchQualificationPlan.ConditionFilter condition,
            UserProductSearchQualificationPlan.LocationFilter shipsTo,
            UserProductSearchQualificationPlan.LocationsFilter shipsFrom,
            UserProductSearchQualificationPlan.PriceFilter price,
            UserProductSearchQualificationPlan.AttributesFilter attributes,
            UserProductSearchQualificationPlan.RatingFilter rating,
            UserProductSearchQualificationPlan.PriceTierFilter priceTier
    ) {
    }

    private record ScopedUserMessage(int index, String text) {
    }

    private UserProductSearchQualificationPlan.ConditionFilter condition(
            UserProductSearchQualificationPlan.ConditionFilter filter,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        if (validResolution(filter.state(), filter.provenance(), UserProductSearchQuestionTarget.CONDITION,
                filter.values().stream().map(Enum::name).toList(), query, violations)) {
            return filter;
        }
        return new UserProductSearchQualificationPlan.ConditionFilter(
                UserProductSearchFilterState.MISSING, List.of(), UserProductSearchQualificationPlan.Provenance.none());
    }

    private UserProductSearchQualificationPlan.LocationFilter shipsTo(
            UserProductSearchQualificationPlan.LocationFilter filter,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        if (filter.state() == UserProductSearchFilterState.VALUE
                && filter.provenance().source() == UserProductSearchDecisionSource.PROFILE) {
            var savedLocation = savedProfileLocation(filter, query.settings());
            if (savedLocation == null) {
                return new UserProductSearchQualificationPlan.LocationFilter(
                        UserProductSearchFilterState.MISSING,
                        null,
                        UserProductSearchQualificationPlan.Provenance.none()
                );
            }
            filter = new UserProductSearchQualificationPlan.LocationFilter(
                    filter.state(),
                    new UserProductSearchQualificationPlan.Location(
                            savedLocation.code(),
                            savedLocation.region(),
                            savedLocation.postalCode()
                    ),
                    new UserProductSearchQualificationPlan.Provenance(
                            UserProductSearchDecisionSource.PROFILE,
                            savedLocation.code()
                    )
            );
        }
        List<String> values = filter.value() == null ? List.of() : List.of(filter.value().country());
        if (filter.state() == UserProductSearchFilterState.VALUE
                && filter.value() != null
                && !locationDetailsMatchEvidence(
                        filter.value(), filter.provenance(), UserProductSearchQuestionTarget.SHIPS_TO, query)) {
            violations.add("SHIPS_TO region or postal code lacks provenance evidence");
            return new UserProductSearchQualificationPlan.LocationFilter(
                    UserProductSearchFilterState.MISSING,
                    null,
                    UserProductSearchQualificationPlan.Provenance.none()
            );
        }
        if (validResolution(filter.state(), filter.provenance(), UserProductSearchQuestionTarget.SHIPS_TO,
                values, query, violations)) {
            return filter;
        }
        return new UserProductSearchQualificationPlan.LocationFilter(
                UserProductSearchFilterState.MISSING, null, UserProductSearchQualificationPlan.Provenance.none());
    }

    private UserProductSearchQualificationPlan.LocationsFilter shipsFrom(
            UserProductSearchQualificationPlan.LocationsFilter filter,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        if (filter.state() == UserProductSearchFilterState.VALUE
                && filter.values().stream()
                        .anyMatch(location -> !locationDetailsMatchEvidence(
                                location,
                                filter.provenance(),
                                UserProductSearchQuestionTarget.SHIPS_FROM,
                                query))) {
            violations.add("SHIPS_FROM region or postal code lacks provenance evidence");
            return new UserProductSearchQualificationPlan.LocationsFilter(
                    UserProductSearchFilterState.MISSING,
                    List.of(),
                    UserProductSearchQualificationPlan.Provenance.none()
            );
        }
        if (validResolution(filter.state(), filter.provenance(), UserProductSearchQuestionTarget.SHIPS_FROM,
                filter.values().stream().map(UserProductSearchQualificationPlan.Location::country).toList(),
                query, violations)) {
            return filter;
        }
        return new UserProductSearchQualificationPlan.LocationsFilter(
                UserProductSearchFilterState.MISSING, List.of(), UserProductSearchQualificationPlan.Provenance.none());
    }

    private boolean locationDetailsMatchEvidence(
            UserProductSearchQualificationPlan.Location location,
            UserProductSearchQualificationPlan.Provenance provenance,
            UserProductSearchQuestionTarget target,
        GenerateUserProductSearchQualificationQuery query
    ) {
        if (provenance.source() == UserProductSearchDecisionSource.PROFILE) {
            UserLocationResult primary = query.settings().location();
            return primary != null
                    && primary.code() != null
                    && primary.code().equalsIgnoreCase(location.country())
                    && java.util.Objects.equals(primary.region(), location.region())
                    && java.util.Objects.equals(primary.postalCode(), location.postalCode());
        }
        String evidence = normalize(provenance.evidence());
        return regionMentioned(
                        provenance.evidence(),
                        evidence,
                        rawSource(provenance, target, query),
                        location.region())
                && optionalPhraseMentioned(evidence, location.postalCode());
    }

    private UserLocationResult savedProfileLocation(
            UserProductSearchQualificationPlan.LocationFilter filter,
            UserSettingsResult settings
    ) {
        UserLocationResult primary = settings.location();
        if (filter.value() == null || primary == null || primary.code() == null) {
            return null;
        }
        return primary.code().equalsIgnoreCase(filter.value().country())
                        && java.util.Objects.equals(primary.region(), filter.value().region())
                        && java.util.Objects.equals(primary.postalCode(), filter.value().postalCode())
                ? primary
                : null;
    }

    private boolean regionMentioned(
            String evidence,
            String normalizedEvidence,
            String rawSource,
            String region
    ) {
        if (region == null) {
            return true;
        }
        String trimmed = region.trim();
        if (trimmed.matches("[A-Za-z]{2}")) {
            String uppercaseRegion = trimmed.toUpperCase(Locale.ROOT);
            return containsUppercaseCode(evidence, uppercaseRegion)
                    && containsUppercaseCode(rawSource, uppercaseRegion);
        }
        return containsPhrase(normalizedEvidence, normalize(trimmed));
    }

    private boolean optionalPhraseMentioned(String evidence, String value) {
        return value == null || containsPhrase(evidence, normalize(value));
    }

    private UserProductSearchQualificationPlan.PriceFilter price(
            UserProductSearchQualificationPlan.PriceFilter filter,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        if (filter.state() == UserProductSearchFilterState.VALUE
                && !priceBoundsMatchEvidence(filter, query)) {
            violations.add("PRICE typed bounds do not match the buyer's grounded bound direction and values");
            return missingPrice();
        }
        if (filter.state() == UserProductSearchFilterState.NOT_APPLICABLE
                && hasPriceBound(query)) {
            violations.add("PRICE cannot be irrelevant while a price bound is present");
            return missingPrice();
        }
        List<String> values = new ArrayList<>();
        if (filter.minUsdMinor() != null) {
            values.add(majorAmount(filter.minUsdMinor(), preferredCurrency(query)));
        }
        if (filter.maxUsdMinor() != null) {
            values.add(majorAmount(filter.maxUsdMinor(), preferredCurrency(query)));
        }
        if (validResolution(filter.state(), filter.provenance(), UserProductSearchQuestionTarget.PRICE,
                values, query, violations)) {
            return filter;
        }
        return new UserProductSearchQualificationPlan.PriceFilter(
                UserProductSearchFilterState.MISSING, null, null,
                UserProductSearchQualificationPlan.Provenance.none());
    }

    private boolean hasPriceBound(GenerateUserProductSearchQualificationQuery query) {
        return priceConstraintSources(query).stream()
                .anyMatch(this::priceExpressionMentioned);
    }

    private List<String> priceConstraintSources(GenerateUserProductSearchQualificationQuery query) {
        return java.util.stream.Stream.of(query.originalQuery(), query.message())
                .filter(source -> source != null && !source.isBlank())
                .distinct()
                .toList();
    }

    private boolean priceExpressionMentioned(String source) {
        return directionalPriceBoundMentioned(source)
                || STANDALONE_DENOMINATED_PRICE_PATTERN.matcher(source).find();
    }

    private boolean directionalPriceBoundMentioned(String source) {
        if (PRICE_MIN_PATTERN.matcher(source).find() || PRICE_MAX_PATTERN.matcher(source).find()) {
            return true;
        }
        java.util.regex.Matcher range = PRICE_RANGE_PATTERN.matcher(source);
        while (range.find()) {
            String expression = range.group();
            if (normalize(expression).startsWith("between")
                    || PRICE_CONTEXT_PATTERN.matcher(expression).find()) {
                return true;
            }
            int contextStart = Math.max(0, range.start() - 24);
            int contextEnd = Math.min(source.length(), range.end() + 24);
            if (PRICE_CONTEXT_PATTERN.matcher(source.substring(contextStart, contextEnd)).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean explicitPreferredCurrencyDenomination(
            String value,
            GenerateUserProductSearchQualificationQuery query
    ) {
        return explicitCurrencyDenomination(value, preferredCurrency(query));
    }

    private boolean explicitCurrencyDenomination(String value, String currency) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(currency)
                + "(?![\\p{L}\\p{N}])").matcher(value).find()) {
            return true;
        }
        return switch (currency) {
            case "USD" -> Pattern.compile("(?iu)\\b(?:u\\.s\\.|us)\\s+dollars?\\b").matcher(value).find();
            case "EUR" -> Pattern.compile("(?iu)\\beuros?\\b|€").matcher(value).find();
            case "GBP" -> Pattern.compile("(?iu)\\b(?:british\\s+)?pounds?\\b|£").matcher(value).find();
            case "CZK" -> Pattern.compile("(?iu)\\b(?:czech\\s+)?crowns?\\b|\\bkorun(?:a|y)?\\b|kč")
                    .matcher(value).find();
            default -> false;
        };
    }

    private String preferredCurrency(GenerateUserProductSearchQualificationQuery query) {
        return query == null || query.settings() == null
                ? UserCurrency.DEFAULT
                : UserCurrency.normalizeOrDefault(query.settings().currency());
    }

    private boolean priceBoundsMatchEvidence(
            UserProductSearchQualificationPlan.PriceFilter filter,
            GenerateUserProductSearchQualificationQuery query
    ) {
        UserProductSearchQualificationPlan.Provenance provenance = filter.provenance();
        String source = rawSource(provenance, UserProductSearchQuestionTarget.PRICE, query);
        if (provenance.source() == UserProductSearchDecisionSource.CONVERSATION
                && priceConstraintSources(query).stream()
                .noneMatch(candidate -> normalize(candidate).equals(normalize(source)))) {
            return false;
        }
        PriceBounds grounded = parsePriceBounds(source);
        if (grounded == null
                && provenance.source() == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && explicitPreferredCurrencyDenomination(provenance.evidence(), query)
                && query.previousPlan() != null
                && query.previousPlan().questionTargets()
                .contains(UserProductSearchQuestionTarget.PRICE)) {
            List<String> sources = priceConstraintSources(query);
            for (int index = sources.size() - 1; index >= 0 && grounded == null; index--) {
                grounded = parsePriceBounds(sources.get(index));
            }
        }
        return grounded != null
                && minorUnitsMatch(filter.minUsdMinor(), grounded.min(), preferredCurrency(query))
                && minorUnitsMatch(filter.maxUsdMinor(), grounded.max(), preferredCurrency(query));
    }

    private PriceBounds parsePriceBounds(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        java.util.regex.Matcher range = PRICE_RANGE_PATTERN.matcher(source);
        if (range.find()) {
            BigDecimal min = parseNumber(range.group("min"));
            BigDecimal max = parseNumber(range.group("max"));
            return min == null || max == null || min.compareTo(max) > 0
                    ? null
                    : new PriceBounds(min, max);
        }
        BigDecimal min = lastPriceAmount(PRICE_MIN_PATTERN, source);
        BigDecimal max = lastPriceAmount(PRICE_MAX_PATTERN, source);
        return min == null && max == null ? null : new PriceBounds(min, max);
    }

    private BigDecimal lastPriceAmount(Pattern pattern, String source) {
        java.util.regex.Matcher matcher = pattern.matcher(source);
        BigDecimal amount = null;
        while (matcher.find()) {
            amount = parseNumber(matcher.group("amount"));
        }
        return amount;
    }

    private boolean minorUnitsMatch(Long actualMinor, BigDecimal expectedMajor, String currency) {
        if (actualMinor == null || expectedMajor == null) {
            return actualMinor == null && expectedMajor == null;
        }
        int fractionDigits = Currency.getInstance(currency).getDefaultFractionDigits();
        return BigDecimal.valueOf(actualMinor, fractionDigits < 0 ? 2 : fractionDigits)
                .compareTo(expectedMajor) == 0;
    }

    private UserProductSearchQualificationPlan.PriceFilter missingPrice() {
        return new UserProductSearchQualificationPlan.PriceFilter(
                UserProductSearchFilterState.MISSING,
                null,
                null,
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.RatingFilter rating(
            UserProductSearchQualificationPlan.RatingFilter filter,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        if (filter.state() == UserProductSearchFilterState.VALUE
                && !ratingValuesMatchEvidence(filter, query)) {
            violations.add("RATING provenance evidence does not support its typed value");
            return new UserProductSearchQualificationPlan.RatingFilter(
                    UserProductSearchFilterState.MISSING, null, null,
                    UserProductSearchQualificationPlan.Provenance.none());
        }
        List<String> values = new ArrayList<>();
        if (filter.min() != null) {
            values.add(filter.min().toPlainString());
        }
        if (filter.minCount() != null) {
            values.add(filter.minCount().toString());
        }
        if (validResolution(filter.state(), filter.provenance(), UserProductSearchQuestionTarget.RATING,
                values, query, violations)) {
            return filter;
        }
        return new UserProductSearchQualificationPlan.RatingFilter(
                UserProductSearchFilterState.MISSING, null, null,
                UserProductSearchQualificationPlan.Provenance.none());
    }

    private UserProductSearchQualificationPlan.PriceTierFilter priceTier(
            UserProductSearchQualificationPlan.PriceTierFilter filter,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        if (validResolution(filter.state(), filter.provenance(), UserProductSearchQuestionTarget.PRICE_TIER,
                filter.values().stream().map(Enum::name).toList(), query, violations)) {
            return filter;
        }
        return new UserProductSearchQualificationPlan.PriceTierFilter(
                UserProductSearchFilterState.MISSING, List.of(), UserProductSearchQualificationPlan.Provenance.none());
    }

    private UserProductSearchQualificationPlan.AttributesFilter attributes(
            UserProductSearchQualificationPlan.AttributesFilter candidate,
            UserProductSearchQualificationPlan.AttributesFilter previous,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> currentByName =
                byAttributeName(candidate.values());
        EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> previousByName =
                previous == null ? new EnumMap<>(UserProductSearchAttributeName.class) : byAttributeName(previous.values());
        List<UserProductSearchQualificationPlan.Attribute> resolved = new ArrayList<>();
        for (UserProductSearchAttributeName name : UserProductSearchAttributeName.values()) {
            UserProductSearchQualificationPlan.Attribute current = currentByName.get(name);
            UserProductSearchQualificationPlan.Attribute previousAttribute = previousByName.get(name);
            if (current == null) {
                violations.add("attributes must contain one decision for " + name);
                current = new UserProductSearchQualificationPlan.Attribute(
                        name,
                        UserProductSearchFilterState.MISSING,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.none()
                );
            }
            if (unchanged(previousAttribute, current)) {
                resolved.add(previousAttribute);
                continue;
            }
            UserProductSearchQuestionTarget target = target(name);
            if (!validResolution(current.state(), current.provenance(), target, current.values(), query, violations)) {
                current = new UserProductSearchQualificationPlan.Attribute(
                        name,
                        UserProductSearchFilterState.MISSING,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.none()
                );
            }
            resolved.add(merge(
                    previousAttribute,
                    current,
                    UserProductSearchQualificationPlan.Attribute::state,
                    UserProductSearchQualificationPlan.Attribute::provenance
            ));
        }
        UserProductSearchFilterState groupState = resolved.stream()
                .anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.MISSING)
                ? UserProductSearchFilterState.MISSING
                : resolved.stream().anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.VALUE)
                ? UserProductSearchFilterState.VALUE
                : resolved.stream().anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.ANY)
                ? UserProductSearchFilterState.ANY
                : UserProductSearchFilterState.NOT_APPLICABLE;
        return new UserProductSearchQualificationPlan.AttributesFilter(groupState, resolved);
    }

    private ActiveRequirementResolution enforceActiveRequestRequirements(
            UserProductSearchQualificationPlan.ConditionFilter condition,
            UserProductSearchQualificationPlan.LocationFilter shipsTo,
            UserProductSearchQualificationPlan.LocationsFilter shipsFrom,
            UserProductSearchQualificationPlan.PriceFilter price,
            UserProductSearchQualificationPlan.AttributesFilter attributes,
            UserProductSearchQualificationPlan.RatingFilter rating,
            UserProductSearchQualificationPlan.PriceTierFilter priceTier,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        ActiveTargetRequirement conditionRequirement =
                activeTargetRequirement(UserProductSearchQuestionTarget.CONDITION, query);
        ActiveTargetRequirement shipsToRequirement =
                activeTargetRequirement(UserProductSearchQuestionTarget.SHIPS_TO, query);
        ActiveTargetRequirement shipsFromRequirement =
                activeTargetRequirement(UserProductSearchQuestionTarget.SHIPS_FROM, query);
        ActiveTargetRequirement priceRequirement =
                activeTargetRequirement(UserProductSearchQuestionTarget.PRICE, query);
        ActiveTargetRequirement colorRequirement =
                activeTargetRequirement(UserProductSearchQuestionTarget.COLOR, query);
        ActiveTargetRequirement sizeRequirement =
                activeTargetRequirement(UserProductSearchQuestionTarget.SIZE, query);
        ActiveTargetRequirement genderRequirement =
                activeTargetRequirement(UserProductSearchQuestionTarget.TARGET_GENDER, query);
        ActiveTargetRequirement ratingRequirement =
                activeTargetRequirement(UserProductSearchQuestionTarget.RATING, query);
        ActiveTargetRequirement priceTierRequirement =
                activeTargetRequirement(UserProductSearchQuestionTarget.PRICE_TIER, query);

        condition = switch (conditionRequirement.kind()) {
            case ANY -> new UserProductSearchQualificationPlan.ConditionFilter(
                    UserProductSearchFilterState.ANY,
                    List.of(),
                    conditionRequirement.provenance()
            );
            case VALUE -> activeValueAccepted(
                    condition.state(), condition.provenance(), conditionRequirement, query)
                    ? condition
                    : missingConditionForActiveRequest(
                            UserProductSearchQuestionTarget.CONDITION, condition.state(), violations);
            case NONE -> condition;
        };
        shipsTo = switch (shipsToRequirement.kind()) {
            case ANY -> new UserProductSearchQualificationPlan.LocationFilter(
                    UserProductSearchFilterState.ANY,
                    null,
                    shipsToRequirement.provenance()
            );
            case VALUE -> activeValueAccepted(
                    shipsTo.state(), shipsTo.provenance(), shipsToRequirement, query)
                    ? shipsTo
                    : missingShipsToForActiveRequest(shipsTo.state(), violations);
            case NONE -> shipsTo;
        };
        shipsFrom = switch (shipsFromRequirement.kind()) {
            case ANY -> new UserProductSearchQualificationPlan.LocationsFilter(
                    UserProductSearchFilterState.ANY,
                    List.of(),
                    shipsFromRequirement.provenance()
            );
            case VALUE -> activeValueAccepted(
                    shipsFrom.state(), shipsFrom.provenance(), shipsFromRequirement, query)
                    ? shipsFrom
                    : missingShipsFromForActiveRequest(shipsFrom.state(), violations);
            case NONE -> shipsFrom;
        };
        price = switch (priceRequirement.kind()) {
            case ANY -> new UserProductSearchQualificationPlan.PriceFilter(
                    UserProductSearchFilterState.ANY,
                    null,
                    null,
                    priceRequirement.provenance()
            );
            case VALUE -> activeValueAccepted(
                    price.state(), price.provenance(), priceRequirement, query)
                    ? price
                    : missingPriceForActiveRequest(price.state(), violations);
            case NONE -> price;
        };
        rating = switch (ratingRequirement.kind()) {
            case ANY -> new UserProductSearchQualificationPlan.RatingFilter(
                    UserProductSearchFilterState.ANY,
                    null,
                    null,
                    ratingRequirement.provenance()
            );
            case VALUE -> activeValueAccepted(
                    rating.state(), rating.provenance(), ratingRequirement, query)
                    ? rating
                    : missingRatingForActiveRequest(rating.state(), violations);
            case NONE -> rating;
        };
        priceTier = switch (priceTierRequirement.kind()) {
            case ANY -> new UserProductSearchQualificationPlan.PriceTierFilter(
                    UserProductSearchFilterState.ANY,
                    List.of(),
                    priceTierRequirement.provenance()
            );
            case VALUE -> activeValueAccepted(
                    priceTier.state(), priceTier.provenance(), priceTierRequirement, query)
                    ? priceTier
                    : missingPriceTierForActiveRequest(priceTier.state(), violations);
            case NONE -> priceTier;
        };

        EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> byName =
                byAttributeName(attributes.values());
        enforceActiveAttribute(
                byName,
                UserProductSearchAttributeName.COLOR,
                UserProductSearchQuestionTarget.COLOR,
                colorRequirement,
                query,
                violations
        );
        enforceActiveAttribute(
                byName,
                UserProductSearchAttributeName.SIZE,
                UserProductSearchQuestionTarget.SIZE,
                sizeRequirement,
                query,
                violations
        );
        enforceActiveAttribute(
                byName,
                UserProductSearchAttributeName.TARGET_GENDER,
                UserProductSearchQuestionTarget.TARGET_GENDER,
                genderRequirement,
                query,
                violations
        );
        List<UserProductSearchQualificationPlan.Attribute> attributeValues =
                java.util.Arrays.stream(UserProductSearchAttributeName.values())
                        .map(byName::get)
                        .toList();
        attributes = new UserProductSearchQualificationPlan.AttributesFilter(
                attributeGroupState(attributeValues),
                attributeValues
        );
        return new ActiveRequirementResolution(
                condition,
                shipsTo,
                shipsFrom,
                price,
                attributes,
                rating,
                priceTier
        );
    }

    private void enforceActiveAttribute(
            EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> byName,
            UserProductSearchAttributeName name,
            UserProductSearchQuestionTarget target,
            ActiveTargetRequirement requirement,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        UserProductSearchQualificationPlan.Attribute attribute = byName.get(name);
        if (requirement.kind() == ActiveRequirementKind.ANY) {
            byName.put(name, new UserProductSearchQualificationPlan.Attribute(
                    name,
                    UserProductSearchFilterState.ANY,
                    List.of(),
                    requirement.provenance()
            ));
        } else if (requirement.kind() == ActiveRequirementKind.VALUE
                && !activeValueAccepted(attribute.state(), attribute.provenance(), requirement, query)) {
            addDroppedActiveConstraintViolation(target, attribute.state(), violations);
            byName.put(name, new UserProductSearchQualificationPlan.Attribute(
                    name,
                    UserProductSearchFilterState.MISSING,
                    List.of(),
                    UserProductSearchQualificationPlan.Provenance.none()
            ));
        }
    }

    private boolean activeValueAccepted(
            UserProductSearchFilterState state,
            UserProductSearchQualificationPlan.Provenance provenance,
            ActiveTargetRequirement requirement,
            GenerateUserProductSearchQualificationQuery query
    ) {
        if (state == UserProductSearchFilterState.MISSING) {
            return true;
        }
        if (state != UserProductSearchFilterState.VALUE || provenance == null) {
            return false;
        }
        if (requirement.source() == UserProductSearchDecisionSource.CURRENT_USER_TURN) {
            return provenance.source() == UserProductSearchDecisionSource.CURRENT_USER_TURN;
        }
        return provenance.source() == UserProductSearchDecisionSource.ORIGINAL_QUERY
                || normalize(query.originalQuery()).equals(normalize(query.message()))
                && provenance.source() == UserProductSearchDecisionSource.CURRENT_USER_TURN;
    }

    private UserProductSearchQualificationPlan.ConditionFilter missingConditionForActiveRequest(
            UserProductSearchQuestionTarget target,
            UserProductSearchFilterState state,
            List<String> violations
    ) {
        addDroppedActiveConstraintViolation(target, state, violations);
        return new UserProductSearchQualificationPlan.ConditionFilter(
                UserProductSearchFilterState.MISSING,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.LocationFilter missingShipsToForActiveRequest(
            UserProductSearchFilterState state,
            List<String> violations
    ) {
        addDroppedActiveConstraintViolation(UserProductSearchQuestionTarget.SHIPS_TO, state, violations);
        return new UserProductSearchQualificationPlan.LocationFilter(
                UserProductSearchFilterState.MISSING,
                null,
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.LocationsFilter missingShipsFromForActiveRequest(
            UserProductSearchFilterState state,
            List<String> violations
    ) {
        addDroppedActiveConstraintViolation(UserProductSearchQuestionTarget.SHIPS_FROM, state, violations);
        return new UserProductSearchQualificationPlan.LocationsFilter(
                UserProductSearchFilterState.MISSING,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.PriceFilter missingPriceForActiveRequest(
            UserProductSearchFilterState state,
            List<String> violations
    ) {
        addDroppedActiveConstraintViolation(UserProductSearchQuestionTarget.PRICE, state, violations);
        return missingPrice();
    }

    private UserProductSearchQualificationPlan.RatingFilter missingRatingForActiveRequest(
            UserProductSearchFilterState state,
            List<String> violations
    ) {
        addDroppedActiveConstraintViolation(UserProductSearchQuestionTarget.RATING, state, violations);
        return new UserProductSearchQualificationPlan.RatingFilter(
                UserProductSearchFilterState.MISSING,
                null,
                null,
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.PriceTierFilter missingPriceTierForActiveRequest(
            UserProductSearchFilterState state,
            List<String> violations
    ) {
        addDroppedActiveConstraintViolation(UserProductSearchQuestionTarget.PRICE_TIER, state, violations);
        return new UserProductSearchQualificationPlan.PriceTierFilter(
                UserProductSearchFilterState.MISSING,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private void addDroppedActiveConstraintViolation(
            UserProductSearchQuestionTarget target,
            UserProductSearchFilterState state,
            List<String> violations
    ) {
        if (state != UserProductSearchFilterState.MISSING) {
            violations.add(target + " cannot drop or override an explicit active-request decision");
        }
    }

    private UserProductSearchFilterState attributeGroupState(
            List<UserProductSearchQualificationPlan.Attribute> attributes
    ) {
        return attributes.stream().anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.MISSING)
                ? UserProductSearchFilterState.MISSING
                : attributes.stream().anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.VALUE)
                ? UserProductSearchFilterState.VALUE
                : attributes.stream().anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.ANY)
                ? UserProductSearchFilterState.ANY
                : UserProductSearchFilterState.NOT_APPLICABLE;
    }

    private ActiveTargetRequirement activeTargetRequirement(
            UserProductSearchQuestionTarget target,
            GenerateUserProductSearchQualificationQuery query
    ) {
        if (!normalize(query.message()).equals(normalize(query.originalQuery()))
                && currentTurnBelongsToActiveProduct(query)) {
            ActiveTargetRequirement current = targetRequirement(
                    target,
                    query.message(),
                    UserProductSearchDecisionSource.CURRENT_USER_TURN,
                    query
            );
            if (current.kind() != ActiveRequirementKind.NONE) {
                return current;
            }
        }
        if (currentTurnBelongsToActiveProduct(query)
                && previousBuyerCorrection(target, query.previousPlan())) {
            return ActiveTargetRequirement.none();
        }
        return targetRequirement(
                target,
                query.originalQuery(),
                UserProductSearchDecisionSource.ORIGINAL_QUERY,
                query
        );
    }

    private boolean previousBuyerCorrection(
            UserProductSearchQuestionTarget target,
            UserProductSearchQualificationPlan previous
    ) {
        if (previous == null || !previous.currentSchema()) {
            return false;
        }
        return switch (target) {
            case CONDITION -> buyerCorrection(previous.condition().state(), previous.condition().provenance());
            case SHIPS_TO -> buyerCorrection(previous.shipsTo().state(), previous.shipsTo().provenance());
            case SHIPS_FROM -> buyerCorrection(previous.shipsFrom().state(), previous.shipsFrom().provenance());
            case PRICE -> buyerCorrection(previous.price().state(), previous.price().provenance());
            case COLOR -> buyerAttributeCorrection(previous, UserProductSearchAttributeName.COLOR);
            case SIZE -> buyerAttributeCorrection(previous, UserProductSearchAttributeName.SIZE);
            case TARGET_GENDER -> buyerAttributeCorrection(previous, UserProductSearchAttributeName.TARGET_GENDER);
            case RATING -> buyerCorrection(previous.rating().state(), previous.rating().provenance());
            case PRICE_TIER -> buyerCorrection(previous.priceTier().state(), previous.priceTier().provenance());
        };
    }

    private boolean buyerAttributeCorrection(
            UserProductSearchQualificationPlan previous,
            UserProductSearchAttributeName name
    ) {
        return previous.attributes().values().stream()
                .filter(attribute -> attribute.name() == name)
                .findFirst()
                .map(attribute -> buyerCorrection(attribute.state(), attribute.provenance()))
                .orElse(false);
    }

    private boolean buyerCorrection(
            UserProductSearchFilterState state,
            UserProductSearchQualificationPlan.Provenance provenance
    ) {
        return resolved(state)
                && provenance != null
                && provenance.evidence() != null
                && !provenance.evidence().isBlank()
                && (provenance.source() == UserProductSearchDecisionSource.CURRENT_USER_TURN
                || provenance.source() == UserProductSearchDecisionSource.CONVERSATION);
    }

    private ActiveTargetRequirement targetRequirement(
            UserProductSearchQuestionTarget target,
            String source,
            UserProductSearchDecisionSource decisionSource,
            GenerateUserProductSearchQualificationQuery query
    ) {
        UserProductSearchQualificationPlan.Provenance provenance =
                new UserProductSearchQualificationPlan.Provenance(decisionSource, source);
        if (explicitIndifference(provenance, target, query.previousPlan())
                || target == UserProductSearchQuestionTarget.SHIPS_TO
                && LOCATION_ANYWHERE_QUERY_PATTERN.matcher(source).find()) {
            return new ActiveTargetRequirement(ActiveRequirementKind.ANY, decisionSource, source);
        }
        return activeValueMentioned(target, source, decisionSource, query)
                ? new ActiveTargetRequirement(ActiveRequirementKind.VALUE, decisionSource, source)
                : ActiveTargetRequirement.none();
    }

    private boolean activeValueMentioned(
            UserProductSearchQuestionTarget target,
            String source,
            UserProductSearchDecisionSource decisionSource,
            GenerateUserProductSearchQualificationQuery query
    ) {
        boolean currentAnswer = decisionSource == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && query.previousPlan() != null
                && query.previousPlan().questionTargets().contains(target);
        return switch (target) {
            case CONDITION -> CONDITION_CONSTRAINT_PATTERN.matcher(source).find();
            case SHIPS_TO -> SHIPS_TO_QUERY_PATTERN.matcher(source).find()
                    || currentAnswer && directLocationAnswerTargets(target, query)
                    && directCountryCode(source).isPresent();
            case SHIPS_FROM -> SHIPS_FROM_QUERY_PATTERN.matcher(source).find()
                    || currentAnswer && directLocationAnswerTargets(target, query)
                    && directCountryCode(source).isPresent();
            case PRICE -> priceExpressionMentioned(source)
                    || currentAnswer && (source.matches("(?iu).*\\d.*")
                    || explicitPreferredCurrencyDenomination(source, query));
            case COLOR -> COLOR_CONSTRAINT_PATTERN.matcher(source).find();
            case SIZE -> SIZE_CONSTRAINT_PATTERN.matcher(source).find()
                    || currentAnswer && directSizeValue(source).isPresent();
            case TARGET_GENDER -> TARGET_GENDER_CONSTRAINT_PATTERN.matcher(source).find();
            case RATING -> RATING_QUERY_PATTERN.matcher(source).find()
                    || currentAnswer && source.matches("(?iu).*\\d.*");
            case PRICE_TIER -> PRICE_TIER_CONSTRAINT_PATTERN.matcher(source).find()
                    || currentAnswer && normalize(source).matches("low|medium|high");
        };
    }

    private boolean directLocationAnswerTargets(
            UserProductSearchQuestionTarget target,
            GenerateUserProductSearchQualificationQuery query
    ) {
        return query.previousPlan().questionTargets().stream()
                .filter(asked -> asked == UserProductSearchQuestionTarget.SHIPS_TO
                        || asked == UserProductSearchQuestionTarget.SHIPS_FROM)
                .allMatch(asked -> asked == target);
    }

    private boolean unchanged(Object previous, Object candidate) {
        return previous != null && previous.equals(candidate);
    }

    private EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> byAttributeName(
            List<UserProductSearchQualificationPlan.Attribute> attributes
    ) {
        EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> byName =
                new EnumMap<>(UserProductSearchAttributeName.class);
        attributes.forEach(attribute -> byName.putIfAbsent(attribute.name(), attribute));
        return byName;
    }

    private boolean validResolution(
            UserProductSearchFilterState state,
            UserProductSearchQualificationPlan.Provenance provenance,
            UserProductSearchQuestionTarget target,
            List<String> values,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        if (state == UserProductSearchFilterState.MISSING || state == UserProductSearchFilterState.NOT_APPLICABLE) {
            if (provenance != null && (provenance.source() != UserProductSearchDecisionSource.NONE
                    || provenance.evidence() != null)) {
                violations.add(target + " unresolved or irrelevant decision must not claim provenance");
                return false;
            }
            return true;
        }
        if (state == UserProductSearchFilterState.VALUE && values.isEmpty()) {
            violations.add(target + " VALUE has no usable value");
            return false;
        }
        if (provenance == null || provenance.source() == UserProductSearchDecisionSource.NONE
                || provenance.source() == UserProductSearchDecisionSource.SYSTEM_POLICY) {
            violations.add(target + " " + state + " has no user-context provenance");
            return false;
        }
        if (state == UserProductSearchFilterState.ANY
                && provenance.source() != UserProductSearchDecisionSource.ORIGINAL_QUERY
                && provenance.source() != UserProductSearchDecisionSource.CURRENT_USER_TURN
                && provenance.source() != UserProductSearchDecisionSource.CONVERSATION) {
            violations.add(target + " ANY must come from an explicit user turn");
            return false;
        }
        if (provenance.source() == UserProductSearchDecisionSource.PROFILE
                && target != UserProductSearchQuestionTarget.SHIPS_TO
                && target != UserProductSearchQuestionTarget.TARGET_GENDER) {
            violations.add(target + " cannot be resolved from the available profile facts");
            return false;
        }
        if (state == UserProductSearchFilterState.ANY
                && !explicitIndifference(provenance, target, query.previousPlan())) {
            violations.add(target + " ANY lacks explicit filter-specific indifference");
            return false;
        }
        if (state == UserProductSearchFilterState.VALUE && isAffirmation(provenance.evidence())) {
            violations.add(target + " VALUE cannot be inferred from a generic affirmation");
            return false;
        }
        if (!evidenceMatches(provenance, target, values, query)) {
            violations.add(target + " provenance evidence does not match its claimed source");
            return false;
        }
        if (state == UserProductSearchFilterState.VALUE
                && !valuesMatchEvidence(target, values, provenance, query)) {
            violations.add(target + " provenance evidence does not support its typed value");
            return false;
        }
        return true;
    }

    private boolean evidenceMatches(
            UserProductSearchQualificationPlan.Provenance provenance,
            UserProductSearchQuestionTarget target,
            List<String> values,
            GenerateUserProductSearchQualificationQuery query
    ) {
        String evidence = normalize(provenance.evidence());
        if (evidence.isBlank()) {
            return false;
        }
        return switch (provenance.source()) {
            case ORIGINAL_QUERY -> containsNormalized(query.originalQuery(), evidence);
            case CURRENT_USER_TURN -> currentTurnBelongsToActiveProduct(query)
                    && containsNormalized(query.message(), evidence);
            case CONVERSATION -> priorUserConversationSource(query, evidence) != null;
            case PROFILE -> containsNormalized(profileContext(query.settings(), target), evidence);
            case DURABLE_PREFERENCE -> target == UserProductSearchQuestionTarget.SIZE
                    && containsNormalized(durableContext(
                            query.durablePreferences(), query.originalQuery()), evidence)
                    && values.stream().allMatch(value -> durableValue(
                            query.durablePreferences(), value, query.originalQuery()));
            case NONE, SYSTEM_POLICY -> false;
        };
    }

    private boolean explicitIndifference(
            UserProductSearchQualificationPlan.Provenance provenance,
            UserProductSearchQuestionTarget target,
            UserProductSearchQualificationPlan previous
    ) {
        String rawEvidence = provenance.evidence() == null ? "" : provenance.evidence();
        String evidence = normalize(rawEvidence);
        if (isAffirmation(evidence)) {
            return previous != null
                    && previous.questionTargets().size() == 1
                    && previous.questionTargets().get(0) == target
                    && containsIndifference(previous.assistantMessage());
        }
        if (!containsIndifference(evidence)) {
            return false;
        }
        boolean targetSpecificIndifference = answerClauses(rawEvidence).stream()
                .anyMatch(clause -> containsIndifference(clause) && mentionsTarget(clause, target));
        if (targetSpecificIndifference) {
            return true;
        }
        if (previous == null || !previous.questionTargets().contains(target)
                || provenance.source() != UserProductSearchDecisionSource.CURRENT_USER_TURN) {
            return false;
        }
        boolean namesAnAskedTarget = previous.questionTargets().stream()
                .anyMatch(asked -> mentionsTarget(evidence, asked));
        return !namesAnAskedTarget || appliesToAll(evidence);
    }

    private String profileContext(UserSettingsResult settings, UserProductSearchQuestionTarget target) {
        List<String> values = new ArrayList<>();
        if (target == UserProductSearchQuestionTarget.TARGET_GENDER) {
            values.add(settings.clothingFit());
        } else if (target == UserProductSearchQuestionTarget.SHIPS_TO) {
            UserLocationResult location = settings.location();
            if (location != null) {
                values.add(location.country());
                values.add(location.code());
                values.add(location.region());
                values.add(location.postalCode());
                values.add(location.regionName());
                values.add(location.city());
            }
        }
        return String.join(" ", values.stream().filter(java.util.Objects::nonNull).toList());
    }

    private String durableContext(
            List<UserProductSearchPreferenceResult> preferences,
            String originalQuery
    ) {
        return preferences.stream()
                .filter(preference -> scopeMatchesQuery(preference.scope(), originalQuery))
                .map(preference -> preference.scope() + " " + preference.attributeName() + " "
                        + String.join(" ", preference.values()))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private boolean durableValue(
            List<UserProductSearchPreferenceResult> preferences,
            String value,
            String originalQuery
    ) {
        return preferences.stream()
                .filter(preference -> preference.attributeName() == UserProductSearchAttributeName.SIZE)
                .filter(preference -> scopeMatchesQuery(preference.scope(), originalQuery))
                .flatMap(preference -> preference.values().stream())
                .anyMatch(stored -> stored.equalsIgnoreCase(value));
    }

    private List<UserProductSearchQualificationPlan.DurableAttribute> durableAttributes(
            List<UserProductSearchQualificationPlan.DurableAttribute> candidates,
            UserProductSearchQualificationPlan.AttributesFilter candidateAttributes,
            UserProductSearchQualificationPlan.AttributesFilter attributes,
            GenerateUserProductSearchQualificationQuery query,
            List<String> violations
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        UserProductSearchQualificationPlan.Attribute candidateSize = candidateAttributes.values().stream()
                .filter(attribute -> attribute.name() == UserProductSearchAttributeName.SIZE)
                .findFirst()
                .orElse(null);
        UserProductSearchQualificationPlan.Attribute size = attributes.values().stream()
                .filter(attribute -> attribute.name() == UserProductSearchAttributeName.SIZE)
                .findFirst()
                .orElse(null);
        boolean candidateIsCurrent = candidateSize != null
                && candidateSize.provenance().source() == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && validResolution(
                        candidateSize.state(),
                        candidateSize.provenance(),
                        UserProductSearchQuestionTarget.SIZE,
                        candidateSize.values(),
                        query,
                        new ArrayList<>()
                );
        if (!candidateIsCurrent || size == null || size.state() != UserProductSearchFilterState.VALUE) {
            violations.add("durable SIZE requires a SIZE value explicitly supplied in the current user turn");
            return List.of();
        }
        List<UserProductSearchQualificationPlan.DurableAttribute> valid = new ArrayList<>();
        for (UserProductSearchQualificationPlan.DurableAttribute candidate : candidates) {
            boolean valuesMatch = !candidate.values().isEmpty()
                    && candidate.values().stream().allMatch(value -> size.values().stream()
                            .anyMatch(effective -> effective.equalsIgnoreCase(value)));
            if (candidate.name() != UserProductSearchAttributeName.SIZE
                    || !valuesMatch
                    || !scopeMatchesQuery(candidate.scope(), query.originalQuery())) {
                violations.add("durable SIZE must match the current query scope and effective SIZE value");
                continue;
            }
            valid.add(candidate);
        }
        return List.copyOf(valid);
    }

    private void validateQuestionCoverage(
            UserProductSearchQualificationPlan plan,
            List<String> violations
    ) {
        LinkedHashSet<UserProductSearchQuestionTarget> missing = new LinkedHashSet<>(plan.missingTargets());
        LinkedHashSet<UserProductSearchQuestionTarget> asked = new LinkedHashSet<>(plan.questionTargets());
        if (!missing.equals(asked)) {
            violations.add("questionTargets must exactly cover unresolved targets; expected " + missing);
        }
        missing.stream()
                .filter(target -> !mentionsTarget(plan.assistantMessage(), target))
                .forEach(target -> violations.add("assistantMessage does not ask for " + target));
    }

    private <T> T merge(
            T previous,
            T current,
            Function<T, UserProductSearchFilterState> state,
            Function<T, UserProductSearchQualificationPlan.Provenance> provenance
    ) {
        if (previous == null) {
            return current;
        }
        if (resolved(state.apply(current))
                && provenance.apply(current).source() == UserProductSearchDecisionSource.CURRENT_USER_TURN) {
            return current;
        }
        if (state.apply(previous) == UserProductSearchFilterState.MISSING
                && state.apply(current) == UserProductSearchFilterState.NOT_APPLICABLE) {
            return previous;
        }
        return resolved(state.apply(previous)) ? previous : current;
    }

    private boolean resolved(UserProductSearchFilterState state) {
        return state == UserProductSearchFilterState.VALUE || state == UserProductSearchFilterState.ANY;
    }

    private UserProductSearchQuestionTarget target(UserProductSearchAttributeName name) {
        return switch (name) {
            case COLOR -> UserProductSearchQuestionTarget.COLOR;
            case SIZE -> UserProductSearchQuestionTarget.SIZE;
            case TARGET_GENDER -> UserProductSearchQuestionTarget.TARGET_GENDER;
        };
    }

    private String label(UserProductSearchQuestionTarget target, String preferredCurrency) {
        return switch (target) {
            case CONDITION -> "condition";
            case SHIPS_TO -> "delivery destination";
            case SHIPS_FROM -> "shipping origin";
            case PRICE -> "price and currency (" + preferredCurrency + " is selected in Account settings)";
            case COLOR -> "color";
            case SIZE -> "size";
            case TARGET_GENDER -> "target gender";
            case RATING -> "rating";
            case PRICE_TIER -> "relative price tier";
        };
    }

    private boolean containsNormalized(String value, String expected) {
        return containsPhrase(normalize(value), expected);
    }

    private boolean valuesMatchEvidence(
            UserProductSearchQuestionTarget target,
            List<String> values,
            UserProductSearchQualificationPlan.Provenance provenance,
            GenerateUserProductSearchQualificationQuery query
    ) {
        String evidence = provenance.evidence();
        String normalizedEvidence = normalize(evidence);
        return values.stream().allMatch(value -> switch (target) {
            case CONDITION -> conditionMentioned(normalizedEvidence, value);
            case SHIPS_TO, SHIPS_FROM -> targetBoundLocationMentioned(target, value, provenance, query);
            case PRICE -> priceNumberMentioned(evidence, value, provenance, query);
            case RATING -> true;
            case COLOR -> colorMentioned(normalizedEvidence, value, provenance, query);
            case SIZE -> sizeMentioned(normalizedEvidence, value, provenance, query);
            case TARGET_GENDER -> targetGenderMentioned(normalizedEvidence, value);
            case PRICE_TIER -> priceTierMentioned(normalizedEvidence, value);
        });
    }

    private boolean conditionMentioned(String evidence, String value) {
        return switch (value) {
            case "NEW" -> NEW_CONDITION_PATTERN.matcher(evidence).find();
            case "SECONDHAND" -> List.of("secondhand", "second hand", "used", "preowned", "pre owned")
                    .stream()
                    .anyMatch(alias -> containsPhrase(evidence, alias));
            default -> false;
        };
    }

    private boolean countryMentioned(
            String evidence,
            String normalizedEvidence,
            String rawSource,
            String value,
            UserProductSearchDecisionSource source
    ) {
        String countryCode = CountryCodeNormalizer.normalizeAlpha2(value);
        if (countryCode == null) {
            return false;
        }
        String displayName = normalize(new Locale.Builder()
                .setRegion(countryCode)
                .build()
                .getDisplayCountry(Locale.ENGLISH));
        boolean directAskedCode = source == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && normalize(rawSource).equals(normalize(countryCode))
                && normalize(evidence).equals(normalize(countryCode));
        boolean codeMentioned = source == UserProductSearchDecisionSource.PROFILE
                ? containsPhrase(normalizedEvidence, normalize(countryCode))
                : containsUppercaseCode(evidence, countryCode)
                        && containsUppercaseCode(rawSource, countryCode);
        if (directAskedCode || codeMentioned || containsPhrase(normalizedEvidence, displayName)) {
            return true;
        }
        return switch (countryCode) {
            case "US" -> containsPhrase(normalizedEvidence, "usa")
                    || containsPhrase(normalizedEvidence, "united states of america");
            case "GB" -> containsPhrase(normalizedEvidence, "uk")
                    || containsPhrase(normalizedEvidence, "great britain");
            default -> false;
        };
    }

    private boolean targetBoundLocationMentioned(
            UserProductSearchQuestionTarget target,
            String value,
            UserProductSearchQualificationPlan.Provenance provenance,
            GenerateUserProductSearchQualificationQuery query
    ) {
        String evidence = provenance.evidence();
        String sourceText = rawSource(provenance, target, query);
        if (!countryMentioned(
                evidence, normalize(evidence), sourceText, value, provenance.source())) {
            return false;
        }
        if (provenance.source() == UserProductSearchDecisionSource.PROFILE) {
            return target == UserProductSearchQuestionTarget.SHIPS_TO;
        }
        if (provenance.source() == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && query.previousPlan() != null
                && query.previousPlan().questionTargets().contains(target)
                && query.previousPlan().questionTargets().stream()
                        .filter(asked -> asked == UserProductSearchQuestionTarget.SHIPS_TO
                                || asked == UserProductSearchQuestionTarget.SHIPS_FROM)
                        .allMatch(asked -> asked == target)) {
            return true;
        }
        return locationRelationMentioned(sourceText, target, value);
    }

    private boolean locationRelationMentioned(
            String source,
            UserProductSearchQuestionTarget target,
            String country
    ) {
        if (source == null) {
            return false;
        }
        java.util.regex.Pattern marker = target == UserProductSearchQuestionTarget.SHIPS_FROM
                ? java.util.regex.Pattern.compile(
                        "(?i)\\b(?:ships?|shipped|shipping)\\s+from\\b|\\bmade\\s+in\\b"
                                + "|\\borigin(?:ating)?\\s+from\\b|\\bfrom\\b")
                : java.util.regex.Pattern.compile(
                        "(?i)\\b(?:ships?|shipped|shipping|deliver|delivered|delivery|send|sent)"
                                + "\\s+(?:it\\s+)?to\\b|\\bto\\b"
                                + "|\\b(?:based|located)\\s+in\\b"
                                + "|\\bi\\s+(?:am|live)\\s+in\\b"
                                + "|\\bi['’]m\\s+in\\b");
        java.util.regex.Matcher matcher = marker.matcher(source);
        while (matcher.find()) {
            int end = Math.min(source.length(), matcher.end() + 80);
            String segment = source.substring(matcher.end(), end);
            java.util.regex.Matcher opposite = (target == UserProductSearchQuestionTarget.SHIPS_FROM
                            ? java.util.regex.Pattern.compile("(?i)\\bto\\b")
                            : java.util.regex.Pattern.compile("(?i)\\bfrom\\b"))
                    .matcher(segment);
            if (opposite.find()) {
                segment = segment.substring(0, opposite.start());
            }
            if (countryMentioned(segment, normalize(segment), segment, country,
                    UserProductSearchDecisionSource.ORIGINAL_QUERY)) {
                return true;
            }
        }
        return false;
    }

    private String rawSource(
            UserProductSearchQualificationPlan.Provenance provenance,
            UserProductSearchQuestionTarget target,
            GenerateUserProductSearchQualificationQuery query
    ) {
        return switch (provenance.source()) {
            case ORIGINAL_QUERY -> query.originalQuery();
            case CURRENT_USER_TURN -> query.message();
            case CONVERSATION -> priorUserConversationSource(query, provenance.evidence());
            case PROFILE -> profileContext(query.settings(), target);
            case DURABLE_PREFERENCE -> durableContext(query.durablePreferences(), query.originalQuery());
            case NONE, SYSTEM_POLICY -> "";
        };
    }

    private String priorUserConversationSource(
            GenerateUserProductSearchQualificationQuery query,
            String evidence
    ) {
        String normalizedEvidence = normalize(evidence);
        if (normalizedEvidence.isBlank()) {
            return null;
        }
        int currentMessageIndex = currentTranscriptMessageIndex(query);
        for (ScopedUserMessage message : activeUserConversationMessages(query)) {
            if (message.index() != currentMessageIndex
                    && containsNormalized(message.text(), normalizedEvidence)) {
                return message.text();
            }
        }
        return null;
    }

    private boolean currentTurnBelongsToActiveProduct(
            GenerateUserProductSearchQualificationQuery query
    ) {
        Optional<UserProductSearchCategoryPolicy.ProductSubject> active =
                explicitProductSubject(query.originalQuery());
        Optional<UserProductSearchCategoryPolicy.ProductSubject> current =
                explicitProductSubject(query.message());
        return active.isEmpty()
                || current.isEmpty()
                || mentionsProductFamily(query.message(), active.get())
                || !establishesProductTopic(query.message())
                || sameProductFamily(active.get(), current.get());
    }

    private List<ScopedUserMessage> activeUserConversationMessages(
            GenerateUserProductSearchQualificationQuery query
    ) {
        int currentMessageIndex = currentTranscriptMessageIndex(query);
        if (currentMessageIndex < 0) {
            return List.of();
        }
        String original = normalize(query.originalQuery());
        int originalMessageIndex = -1;
        for (int index = currentMessageIndex; index >= 0; index--) {
            var message = query.conversation().get(index);
            if (message.role()
                    == com.meant.api.module.user.service.dto.UserProductSearchConversationMessage.Role.USER
                    && normalize(message.text()).equals(original)) {
                originalMessageIndex = index;
                break;
            }
        }
        if (originalMessageIndex < 0) {
            return List.of();
        }

        Optional<UserProductSearchCategoryPolicy.ProductSubject> activeSubject =
                explicitProductSubject(query.originalQuery());
        String activeAssistant = query.previousPlan() == null
                ? ""
                : normalize(query.previousPlan().assistantMessage());
        boolean activeTopic = true;
        List<ScopedUserMessage> scoped = new ArrayList<>();
        for (int index = originalMessageIndex; index <= currentMessageIndex; index++) {
            var message = query.conversation().get(index);
            if (message.role()
                    == com.meant.api.module.user.service.dto.UserProductSearchConversationMessage.Role.ASSISTANT) {
                if (!activeAssistant.isBlank()
                        && normalize(message.text()).equals(activeAssistant)) {
                    activeTopic = true;
                }
                continue;
            }
            if (message.role()
                    != com.meant.api.module.user.service.dto.UserProductSearchConversationMessage.Role.USER) {
                continue;
            }
            if (index == originalMessageIndex) {
                scoped.add(new ScopedUserMessage(index, message.text()));
                continue;
            }
            Optional<UserProductSearchCategoryPolicy.ProductSubject> messageSubject =
                    explicitProductSubject(message.text());
            if (activeSubject.isPresent() && messageSubject.isPresent()) {
                if (mentionsProductFamily(message.text(), activeSubject.get())) {
                    activeTopic = true;
                } else if (establishesProductTopic(message.text())) {
                    activeTopic = sameProductFamily(activeSubject.get(), messageSubject.get());
                }
            }
            if (activeTopic) {
                scoped.add(new ScopedUserMessage(index, message.text()));
            }
        }
        return List.copyOf(scoped);
    }

    private Optional<UserProductSearchCategoryPolicy.ProductSubject> explicitProductSubject(String value) {
        if (directCountryCode(value).isPresent()) {
            return Optional.empty();
        }
        Optional<UserProductSearchCategoryPolicy.ProductSubject> subject =
                categoryPolicy.productSubject(value);
        if (subject.isEmpty()) {
            return subject;
        }
        if (subject.get().terms().stream().allMatch(FILTER_DECISION_SUBJECT_TERMS::contains)) {
            return Optional.empty();
        }
        if (subject.get().terms().size() > 1) {
            return subject;
        }
        UserProductSearchCategoryPolicy.Category category = categoryPolicy.category(value, value);
        return category == UserProductSearchCategoryPolicy.Category.OTHER
                ? Optional.empty()
                : subject;
    }

    private boolean establishesProductTopic(String value) {
        return categoryPolicy.category(value, value) != UserProductSearchCategoryPolicy.Category.OTHER
                || PRODUCT_REQUEST_PATTERN.matcher(value).find();
    }

    private boolean mentionsProductFamily(
            String value,
            UserProductSearchCategoryPolicy.ProductSubject subject
    ) {
        return tokens(value).stream().anyMatch(token -> sameWord(token, subject.head()));
    }

    private boolean sameProductFamily(
            UserProductSearchCategoryPolicy.ProductSubject left,
            UserProductSearchCategoryPolicy.ProductSubject right
    ) {
        return sameWord(left.head(), right.head());
    }

    private int currentTranscriptMessageIndex(GenerateUserProductSearchQualificationQuery query) {
        String current = normalize(query.message());
        int currentMessageIndex = -1;
        for (int index = 0; index < query.conversation().size(); index++) {
            var message = query.conversation().get(index);
            if (message.role()
                    == com.meant.api.module.user.service.dto.UserProductSearchConversationMessage.Role.USER
                    && normalize(message.text()).equals(current)) {
                currentMessageIndex = index;
            }
        }
        return currentMessageIndex;
    }

    private boolean containsUppercaseCode(String evidence, String countryCode) {
        return java.util.regex.Pattern
                .compile("(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(countryCode)
                        + "(?![\\p{L}\\p{N}])")
                .matcher(evidence == null ? "" : evidence)
                .find();
    }

    private boolean targetGenderMentioned(String evidence, String value) {
        return switch (normalize(value)) {
            case "male", "men", "man" -> List.of("male", "men", "man")
                    .stream().anyMatch(alias -> containsPhrase(evidence, alias));
            case "female", "women", "woman" -> List.of("female", "women", "woman")
                    .stream().anyMatch(alias -> containsPhrase(evidence, alias));
            case "unisex" -> containsPhrase(evidence, "unisex");
            default -> containsPhrase(evidence, normalize(value));
        };
    }

    private boolean colorMentioned(
            String evidence,
            String value,
            UserProductSearchQualificationPlan.Provenance provenance,
            GenerateUserProductSearchQualificationQuery query
    ) {
        boolean valuePresent = containsPhrase(evidence, normalize(value));
        if (!valuePresent || explicitlyNegatesValue(evidence, value)) {
            return false;
        }
        return containsPhrase(evidence, "color")
                || containsPhrase(evidence, "colour")
                || provenance.source() == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && query.previousPlan() != null
                && query.previousPlan().questionTargets().size() == 1
                && query.previousPlan().questionTargets().getFirst() == UserProductSearchQuestionTarget.COLOR
                || knownColor(value);
    }

    private boolean explicitlyNegatesValue(String evidence, String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.isBlank()) {
            return false;
        }
        return Pattern.compile(
                        "(?iu)\\b(?:without|avoid(?:ing)?|not|no)\\s+"
                                + "(?:(?:a|an|the)\\s+)?"
                                + Pattern.quote(normalizedValue)
                                + "\\b"
                )
                .matcher(evidence)
                .find();
    }

    private boolean knownColor(String value) {
        return Set.of(
                        "black", "white", "red", "blue", "green", "brown", "grey", "gray", "pink",
                        "purple", "orange", "yellow", "beige", "navy", "teal", "gold", "silver")
                .contains(normalize(value));
    }

    private boolean sizeMentioned(
            String evidence,
            String value,
            UserProductSearchQualificationPlan.Provenance provenance,
            GenerateUserProductSearchQualificationQuery query
    ) {
        if (!containsPhrase(evidence, normalize(value))) {
            return false;
        }
        return containsPhrase(evidence, "size")
                || provenance.source() == UserProductSearchDecisionSource.DURABLE_PREFERENCE
                || provenance.source() == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && query.previousPlan() != null
                && query.previousPlan().questionTargets().contains(UserProductSearchQuestionTarget.SIZE);
    }

    private boolean priceTierMentioned(String evidence, String value) {
        return switch (value) {
            case "LOW" -> List.of("low", "cheap", "budget", "affordable", "inexpensive")
                    .stream().anyMatch(alias -> containsPhrase(evidence, alias));
            case "MEDIUM" -> List.of("medium", "midrange", "mid range")
                    .stream().anyMatch(alias -> containsPhrase(evidence, alias));
            case "HIGH" -> List.of("high", "premium", "luxury", "expensive")
                    .stream().anyMatch(alias -> containsPhrase(evidence, alias));
            default -> false;
        };
    }

    private boolean numberMentioned(String evidence, String expected) {
        if ("0".equals(expected) && containsPhrase(normalize(evidence), "free")) {
            return true;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[0-9]+(?:[.,][0-9]+)*")
                .matcher(evidence == null ? "" : evidence);
        BigDecimal expectedNumber;
        try {
            expectedNumber = new BigDecimal(expected).stripTrailingZeros();
        } catch (NumberFormatException exception) {
            return false;
        }
        while (matcher.find()) {
            BigDecimal mentioned = parseNumber(matcher.group());
            if (mentioned != null && mentioned.compareTo(expectedNumber) == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean priceNumberMentioned(
            String evidence,
            String expected,
            UserProductSearchQualificationPlan.Provenance provenance,
            GenerateUserProductSearchQualificationQuery query
    ) {
        List<String> relationPatterns = List.of(
                "(?i)[$€£]\\s*([0-9]+(?:[.,][0-9]+)*)",
                "(?i)\\b(?:usd|eur|gbp)\\s*([0-9]+(?:[.,][0-9]+)*)",
                "(?i)([0-9]+(?:[.,][0-9]+)*)\\s*(?:usd|eur|gbp)\\b",
                "(?i)\\b(?:price|budget|cost|under|below|over|above|between)\\b"
                        + "(?:\\s+(?:is|of|from|around|up\\s+to))?\\s*[$€£]?"
                        + "\\s*([0-9]+(?:[.,][0-9]+)*)",
                "(?i)\\bbetween\\b[^0-9]{0,12}[0-9]+(?:[.,][0-9]+)*"
                        + "\\s+(?:and|to)\\s*[$€£]?\\s*([0-9]+(?:[.,][0-9]+)*)"
        );
        boolean relationBound = relationNumberMentioned(
                evidence,
                expected,
                relationPatterns
        ) || "0".equals(expected) && containsPhrase(normalize(evidence), "free");
        boolean currencyContinuation = provenance.source()
                == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && explicitPreferredCurrencyDenomination(evidence, query)
                && query.previousPlan() != null
                && query.previousPlan().questionTargets()
                .contains(UserProductSearchQuestionTarget.PRICE)
                && priceConstraintSources(query).stream()
                        .anyMatch(source -> relationNumberMentioned(source, expected, relationPatterns));
        return relationBound
                || currencyContinuation
                || provenance.source() == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && query.previousPlan() != null
                && query.previousPlan().questionTargets().size() == 1
                && query.previousPlan().questionTargets().getFirst() == UserProductSearchQuestionTarget.PRICE
                && numberMentioned(evidence, expected);
    }

    private boolean ratingValuesMatchEvidence(
            UserProductSearchQualificationPlan.RatingFilter filter,
            GenerateUserProductSearchQualificationQuery query
    ) {
        String evidence = filter.provenance().evidence();
        String source = rawSource(
                filter.provenance(), UserProductSearchQuestionTarget.RATING, query);
        boolean directAnswer = filter.provenance().source() == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && query.previousPlan() != null
                && query.previousPlan().questionTargets().size() == 1
                && query.previousPlan().questionTargets().getFirst() == UserProductSearchQuestionTarget.RATING;
        if (filter.min() != null
                && (!numberMentioned(evidence, filter.min().toPlainString())
                || !directAnswer && !relationNumberMentioned(
                        source,
                        filter.min().toPlainString(),
                        List.of(
                                "(?i)\\b(?:rated?|rating)\\b(?:\\s+(?:at|of|is|minimum))?"
                                        + "\\s*([0-9]+(?:[.,][0-9]+)*)",
                                "(?i)([0-9]+(?:[.,][0-9]+)*)\\s*stars?\\b"
                        )))) {
            return false;
        }
        return filter.minCount() == null
                || numberMentioned(evidence, filter.minCount().toString())
                && (directAnswer || relationNumberMentioned(
                        source,
                        filter.minCount().toString(),
                        List.of(
                                "(?i)([0-9]+(?:[.,][0-9]+)*)\\s*reviews?\\b",
                                "(?i)\\breviews?\\b(?:\\s+(?:at\\s+least|minimum|count|of))?"
                                        + "\\s*([0-9]+(?:[.,][0-9]+)*)"
                        )));
    }

    private boolean relationNumberMentioned(String source, String expected, List<String> patterns) {
        BigDecimal expectedNumber;
        try {
            expectedNumber = new BigDecimal(expected).stripTrailingZeros();
        } catch (NumberFormatException exception) {
            return false;
        }
        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern)
                    .matcher(source == null ? "" : source);
            while (matcher.find()) {
                BigDecimal mentioned = parseNumber(matcher.group(1));
                if (mentioned != null && mentioned.compareTo(expectedNumber) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private BigDecimal parseNumber(String token) {
        boolean hasDot = token.indexOf('.') >= 0;
        boolean hasComma = token.indexOf(',') >= 0;
        String normalized;
        if (hasDot && hasComma) {
            boolean dotIsDecimal = token.lastIndexOf('.') > token.lastIndexOf(',');
            String pattern = dotIsDecimal
                    ? "[0-9]{1,3}(?:,[0-9]{3})*\\.[0-9]{1,2}"
                    : "[0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{1,2}";
            if (!token.matches(pattern)) {
                return null;
            }
            normalized = dotIsDecimal
                    ? token.replace(",", "")
                    : token.replace(".", "").replace(',', '.');
        } else if (hasDot || hasComma) {
            char separator = hasDot ? '.' : ',';
            String escapedSeparator = separator == '.' ? "\\." : ",";
            if (token.matches("[0-9]{1,3}(?:" + escapedSeparator + "[0-9]{3})+")) {
                normalized = token.replace(String.valueOf(separator), "");
            } else if (token.matches("[0-9]+" + escapedSeparator + "[0-9]{1,2}")) {
                normalized = separator == ',' ? token.replace(',', '.') : token;
            } else {
                return null;
            }
        } else {
            normalized = token;
        }
        try {
            return new BigDecimal(normalized).stripTrailingZeros();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String majorAmount(long minor, String currency) {
        int fractionDigits = Currency.getInstance(currency).getDefaultFractionDigits();
        return BigDecimal.valueOf(minor, fractionDigits < 0 ? 2 : fractionDigits)
                .stripTrailingZeros()
                .toPlainString();
    }

    private record PriceBounds(BigDecimal min, BigDecimal max) {
    }

    private boolean containsIndifference(String value) {
        String normalized = normalize(value);
        return List.of(
                "any", "either is fine", "doesnt matter", "does not matter", "dont care", "do not care",
                        "no preference", "whatever", "neither", "none", "irrelevant", "not important",
                        "all are fine", "all is fine",
                        "je mi to jedno", "je ti to jedno", "je ti opravdu jedno", "je vam to jedno",
                        "je vam opravdu jedno", "nezalezi", "nemam preferenci", "neresim", "libovolny", "libovolna",
                        "libovolne", "jakykoli", "jakakoli", "cokoli"
                ).stream()
                .anyMatch(phrase -> containsPhrase(normalized, phrase));
    }

    private boolean mentionsTarget(String value, UserProductSearchQuestionTarget target) {
        String normalized = normalize(value);
        if (target == UserProductSearchQuestionTarget.SHIPS_TO) {
            boolean explicitDestination = List.of(
                            "ships to", "ship to", "delivery", "destination", "deliver to",
                            "shipping destination", "shipping location", "postal", "postcode",
                            "doruceni", "dodat"
                    )
                    .stream()
                    .anyMatch(alias -> containsPhrase(normalized, alias));
            if (explicitDestination) {
                return true;
            }
            boolean explicitOrigin = List.of(
                            "ships from", "ship from", "shipping origin", "seller location",
                            "source country", "origin", "odeslani", "odkud"
                    )
                    .stream()
                    .anyMatch(alias -> containsPhrase(normalized, alias));
            return !explicitOrigin
                    && List.of("location", "country")
                    .stream()
                    .anyMatch(alias -> containsPhrase(normalized, alias));
        }
        if (target == UserProductSearchQuestionTarget.PRICE) {
            String withoutPriceTier = normalized
                    .replaceAll("\\b(?:relative\\s+)?price\\s+tier\\b", " ")
                    .replaceAll("\\brelative\\s+price\\b", " ")
                    .replaceAll("\\bcenova\\s+uroven\\b", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            return List.of("budget", "cost", "spend", "cena", "rozpocet")
                    .stream()
                    .anyMatch(alias -> containsPhrase(normalized, alias))
                    || containsPhrase(withoutPriceTier, "price");
        }
        List<String> aliases = switch (target) {
            case CONDITION -> List.of("condition", "new", "used", "secondhand", "stav");
            case SHIPS_TO, PRICE -> throw new IllegalStateException("Handled before alias lookup");
            case SHIPS_FROM -> List.of(
                    "ships from", "ship from", "shipping origin", "seller location", "origin", "odeslani", "odkud");
            case COLOR -> List.of("color", "colour", "barva");
            case SIZE -> List.of("size", "sizing", "velikost");
            case TARGET_GENDER -> List.of(
                    "target gender", "gender", "men", "women", "unisex", "who is it for", "pohlavi", "pro koho");
            case RATING -> List.of("rating", "reviews", "stars", "hodnoceni", "recenze");
            case PRICE_TIER -> List.of("price tier", "relative price", "cheap", "premium", "cenova uroven");
        };
        return aliases.stream().anyMatch(alias -> containsPhrase(normalized, alias));
    }

    private boolean appliesToAll(String value) {
        return List.of(
                        "all", "both", "neither", "all of them", "any of them", "none of them", "everything",
                        "no filters",
                        "vse", "vsechny", "vsechno", "vsechny filtry"
                )
                .stream()
                .anyMatch(phrase -> containsPhrase(value, phrase));
    }

    private boolean isAffirmation(String value) {
        return Set.of("yes", "yes please", "yeah", "yep", "ano", "jo").contains(normalize(value));
    }

    private boolean containsPhrase(String value, String phrase) {
        return (" " + value + " ").contains(" " + phrase + " ");
    }

    private boolean scopeMatchesQuery(String scope, String query) {
        String normalizedScope = normalize(scope == null ? null : scope.replace('-', ' '));
        if (normalizedScope.isBlank()) {
            return false;
        }
        return containsPhrase(normalize(query), normalizedScope);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replaceAll("n't\\b", "nt")
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public record Resolution(UserProductSearchQualificationPlan plan, List<String> violations) {
        public boolean valid() {
            return violations.isEmpty();
        }
    }
}
