package com.meant.api.module.user.service;

import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Applies server-owned policy, provenance checks, and multi-turn accumulation to an LLM candidate plan. */
@Component
public class UserProductSearchQualificationPlanResolver {

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

        var condition = merge(
                previous == null ? null : previous.condition(),
                condition(candidate.condition(), query, violations),
                UserProductSearchQualificationPlan.ConditionFilter::state,
                UserProductSearchQualificationPlan.ConditionFilter::provenance
        );
        var shipsTo = merge(
                previous == null ? null : previous.shipsTo(),
                shipsTo(candidate.shipsTo(), query, violations),
                UserProductSearchQualificationPlan.LocationFilter::state,
                UserProductSearchQualificationPlan.LocationFilter::provenance
        );
        var shipsFrom = merge(
                previous == null ? null : previous.shipsFrom(),
                shipsFrom(candidate.shipsFrom(), query, violations),
                UserProductSearchQualificationPlan.LocationsFilter::state,
                UserProductSearchQualificationPlan.LocationsFilter::provenance
        );
        var price = merge(
                previous == null ? null : previous.price(),
                price(candidate.price(), query, violations),
                UserProductSearchQualificationPlan.PriceFilter::state,
                UserProductSearchQualificationPlan.PriceFilter::provenance
        );
        var rating = merge(
                previous == null ? null : previous.rating(),
                rating(candidate.rating(), query, violations),
                UserProductSearchQualificationPlan.RatingFilter::state,
                UserProductSearchQualificationPlan.RatingFilter::provenance
        );
        var priceTier = merge(
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

        UserProductSearchQualificationPlan resolved = new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                candidate.effectiveQuery(),
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
                durableAttributes(candidate.durableAttributes(), candidate.attributes(), attributes, query, violations)
        );
        resolved = categoryPolicy.enforce(resolved, query);
        validateQuestionCoverage(resolved, violations);
        return new Resolution(resolved, List.copyOf(violations));
    }

    public UserProductSearchQualificationPlan safeFallback(UserProductSearchQualificationPlan plan) {
        List<UserProductSearchQuestionTarget> missing = plan.missingTargets();
        if (missing.isEmpty()) {
            return plan.withConversation("I have everything I need to search.", List.of(), List.of());
        }
        if (missing.size() == 2
                && missing.contains(UserProductSearchQuestionTarget.SIZE)
                && missing.contains(UserProductSearchQuestionTarget.SHIPS_TO)) {
            return plan.withConversation(
                    "What boot size do you need, and what country or postal code should they ship to?",
                    List.of(),
                    missing
            );
        }
        String labels = missing.stream().map(this::label).reduce((left, right) -> left + ", " + right).orElse("");
        return plan.withConversation(
                "I still need your preferences for " + labels + ". Please answer each one, or say explicitly "
                        + "which ones do not matter to you.",
                List.of(),
                missing
        );
    }

    public UserProductSearchQualificationPlan safeFallback(GenerateUserProductSearchQualificationQuery query) {
        if (!categoryPolicy.permitsConservativeFallback(query)) {
            throw new OpenRouterException(
                    "Product-search qualification failed and no conservative category fallback was available");
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
        return safeFallback(categoryPolicy.enforce(fallback, query));
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
                violations.add("SHIPS_TO profile value does not match a saved location");
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
                    filter.provenance()
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
            return safe(query.settings().locations()).stream().anyMatch(saved ->
                    saved.code().equalsIgnoreCase(location.country())
                            && java.util.Objects.equals(saved.region(), location.region())
                            && java.util.Objects.equals(saved.postalCode(), location.postalCode()));
        }
        String evidence = normalize(provenance.evidence());
        return regionMentioned(
                        provenance.evidence(),
                        evidence,
                        rawSource(provenance.source(), target, query),
                        location.region())
                && optionalPhraseMentioned(evidence, location.postalCode());
    }

    private UserLocationResult savedProfileLocation(
            UserProductSearchQualificationPlan.LocationFilter filter,
            UserSettingsResult settings
    ) {
        if (filter.value() == null) {
            return null;
        }
        String evidence = normalize(filter.provenance().evidence());
        List<UserLocationResult> countryMatches = safe(settings.locations()).stream()
                .filter(location -> location.code().equalsIgnoreCase(filter.value().country()))
                .toList();
        if (countryMatches.size() <= 1) {
            return countryMatches.isEmpty() ? null : countryMatches.get(0);
        }
        return countryMatches.stream()
                .filter(location -> containsPhrase(evidence, normalize(location.city()))
                        || containsPhrase(evidence, normalize(location.regionName()))
                        || containsPhrase(evidence, normalize(location.postalCode())))
                .findFirst()
                .orElse(countryMatches.get(0));
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
        List<String> values = new ArrayList<>();
        if (filter.minUsdMinor() != null) {
            values.add(usdMajor(filter.minUsdMinor()));
        }
        if (filter.maxUsdMinor() != null) {
            values.add(usdMajor(filter.maxUsdMinor()));
        }
        if (validResolution(filter.state(), filter.provenance(), UserProductSearchQuestionTarget.PRICE,
                values, query, violations)) {
            return filter;
        }
        return new UserProductSearchQualificationPlan.PriceFilter(
                UserProductSearchFilterState.MISSING, null, null,
                UserProductSearchQualificationPlan.Provenance.none());
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
            if (current == null) {
                violations.add("attributes must contain one decision for " + name);
                current = new UserProductSearchQualificationPlan.Attribute(
                        name,
                        UserProductSearchFilterState.MISSING,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.none()
                );
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
                    previousByName.get(name),
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
                && provenance.source() != UserProductSearchDecisionSource.CURRENT_USER_TURN) {
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
        if (state == UserProductSearchFilterState.VALUE
                && !valuesMatchEvidence(target, values, provenance, query)) {
            violations.add(target + " provenance evidence does not support its typed value");
            return false;
        }
        if (!evidenceMatches(provenance, target, values, query)) {
            violations.add(target + " provenance evidence does not match its claimed source");
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
            case CURRENT_USER_TURN -> containsNormalized(query.message(), evidence);
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
        String evidence = normalize(provenance.evidence());
        if (isAffirmation(evidence)) {
            return previous != null
                    && previous.questionTargets().size() == 1
                    && previous.questionTargets().get(0) == target
                    && containsIndifference(previous.assistantMessage());
        }
        if (!containsIndifference(evidence)) {
            return false;
        }
        if (mentionsTarget(evidence, target)) {
            return true;
        }
        if (previous == null || !previous.questionTargets().contains(target)
                || provenance.source() != UserProductSearchDecisionSource.CURRENT_USER_TURN) {
            return false;
        }
        return previous.questionTargets().size() == 1 || appliesToAll(evidence);
    }

    private String profileContext(UserSettingsResult settings, UserProductSearchQuestionTarget target) {
        List<String> values = new ArrayList<>();
        if (target == UserProductSearchQuestionTarget.TARGET_GENDER) {
            values.add(settings.clothingFit());
        } else if (target == UserProductSearchQuestionTarget.SHIPS_TO) {
            safe(settings.locations()).forEach(location -> {
                values.add(location.country());
                values.add(location.code());
                values.add(location.region());
                values.add(location.postalCode());
                values.add(location.regionName());
                values.add(location.city());
            });
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

    private String label(UserProductSearchQuestionTarget target) {
        return switch (target) {
            case CONDITION -> "condition";
            case SHIPS_TO -> "delivery destination";
            case SHIPS_FROM -> "shipping origin";
            case PRICE -> "price";
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
        String rawSource = rawSource(provenance.source(), target, query);
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
            case "NEW" -> containsPhrase(evidence, "new");
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
        boolean codeMentioned = source == UserProductSearchDecisionSource.PROFILE
                ? containsPhrase(normalizedEvidence, normalize(countryCode))
                : containsUppercaseCode(evidence, countryCode)
                        && containsUppercaseCode(rawSource, countryCode);
        if (codeMentioned || containsPhrase(normalizedEvidence, displayName)) {
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
        String sourceText = rawSource(provenance.source(), target, query);
        if (!countryMentioned(
                evidence, normalize(evidence), sourceText, value, provenance.source())) {
            return false;
        }
        if (provenance.source() == UserProductSearchDecisionSource.PROFILE) {
            return target == UserProductSearchQuestionTarget.SHIPS_TO;
        }
        if (provenance.source() == UserProductSearchDecisionSource.CURRENT_USER_TURN
                && query.previousPlan() != null
                && query.previousPlan().questionTargets().size() == 1
                && query.previousPlan().questionTargets().getFirst() == target) {
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
                                + "\\s+(?:it\\s+)?to\\b|\\bto\\b");
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
            UserProductSearchDecisionSource source,
            UserProductSearchQuestionTarget target,
            GenerateUserProductSearchQualificationQuery query
    ) {
        return switch (source) {
            case ORIGINAL_QUERY -> query.originalQuery();
            case CURRENT_USER_TURN -> query.message();
            case PROFILE -> profileContext(query.settings(), target);
            case DURABLE_PREFERENCE -> durableContext(query.durablePreferences(), query.originalQuery());
            case NONE, SYSTEM_POLICY -> "";
        };
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
        if (!valuePresent) {
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
                && query.previousPlan().questionTargets().size() == 1
                && query.previousPlan().questionTargets().getFirst() == UserProductSearchQuestionTarget.SIZE;
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
        boolean relationBound = relationNumberMentioned(
                evidence,
                expected,
                List.of(
                        "(?i)[$€£]\\s*([0-9]+(?:[.,][0-9]+)*)",
                        "(?i)\\b(?:usd|eur|gbp)\\s*([0-9]+(?:[.,][0-9]+)*)",
                        "(?i)([0-9]+(?:[.,][0-9]+)*)\\s*(?:usd|eur|gbp)\\b",
                        "(?i)\\b(?:price|budget|cost|under|below|over|above|between)\\b"
                                + "(?:\\s+(?:is|of|from|around|up\\s+to))?\\s*[$€£]?"
                                + "\\s*([0-9]+(?:[.,][0-9]+)*)",
                        "(?i)\\bbetween\\b[^0-9]{0,12}[0-9]+(?:[.,][0-9]+)*"
                                + "\\s+(?:and|to)\\s*[$€£]?\\s*([0-9]+(?:[.,][0-9]+)*)"
                )
        ) || "0".equals(expected) && containsPhrase(normalize(evidence), "free");
        return relationBound
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
                filter.provenance().source(), UserProductSearchQuestionTarget.RATING, query);
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

    private String usdMajor(long minor) {
        return BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString();
    }

    private boolean containsIndifference(String value) {
        String normalized = normalize(value);
        return List.of(
                        "any", "either is fine", "doesnt matter", "does not matter", "dont care", "do not care",
                        "no preference", "whatever", "irrelevant", "not important", "all are fine", "all is fine",
                        "je mi to jedno", "je ti to jedno", "je ti opravdu jedno", "je vam to jedno",
                        "je vam opravdu jedno", "nezalezi", "nemam preferenci", "neresim", "libovolny", "libovolna",
                        "libovolne", "jakykoli", "jakakoli", "cokoli"
                ).stream()
                .anyMatch(phrase -> containsPhrase(normalized, phrase));
    }

    private boolean mentionsTarget(String value, UserProductSearchQuestionTarget target) {
        String normalized = normalize(value);
        List<String> aliases = switch (target) {
            case CONDITION -> List.of("condition", "new", "used", "secondhand", "stav");
            case SHIPS_TO -> List.of(
                    "ships to", "ship to", "delivery", "destination", "deliver to", "doruceni", "dodat");
            case SHIPS_FROM -> List.of(
                    "ships from", "ship from", "shipping origin", "seller location", "origin", "odeslani", "odkud");
            case PRICE -> List.of("price", "budget", "cost", "spend", "cena", "rozpocet");
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
                        "all", "all of them", "any of them", "none of them", "everything", "no filters",
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
