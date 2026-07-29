package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterKind;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Server-owned, provider-neutral filter plan produced before catalog discovery. */
public record UserProductSearchQualificationPlan(
        Integer schemaVersion,
        String effectiveQuery,
        String assistantMessage,
        List<String> suggestedReplies,
        List<UserProductSearchQuestionTarget> questionTargets,
        AvailableFilter available,
        ConditionFilter condition,
        LocationFilter shipsTo,
        LocationsFilter shipsFrom,
        PriceFilter price,
        ReferenceFilter shops,
        ReferenceFilter categories,
        AttributesFilter attributes,
        RatingFilter rating,
        PriceTierFilter priceTier,
        List<DurableAttribute> durableAttributes
) {

    public static final int CURRENT_SCHEMA_VERSION = 3;

    public UserProductSearchQualificationPlan {
        schemaVersion = schemaVersion == null || schemaVersion <= 0 ? 1 : schemaVersion;
        effectiveQuery = requiredText(effectiveQuery, "Effective query");
        assistantMessage = requiredText(assistantMessage, "Assistant message");
        suggestedReplies = suggestedReplies == null ? List.of() : List.copyOf(suggestedReplies);
        questionTargets = questionTargets == null ? List.of() : questionTargets.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Objects.requireNonNull(available, "Available filter is required");
        Objects.requireNonNull(condition, "Condition filter is required");
        Objects.requireNonNull(shipsTo, "Ships-to filter is required");
        Objects.requireNonNull(shipsFrom, "Ships-from filter is required");
        Objects.requireNonNull(price, "Price filter is required");
        Objects.requireNonNull(shops, "Shops filter is required");
        Objects.requireNonNull(categories, "Categories filter is required");
        Objects.requireNonNull(attributes, "Attributes filter is required");
        Objects.requireNonNull(rating, "Rating filter is required");
        Objects.requireNonNull(priceTier, "Price-tier filter is required");
        durableAttributes = durableAttributes == null ? List.of() : List.copyOf(durableAttributes);
    }

    /** Compatibility constructor for the persisted version-one plan shape. */
    public UserProductSearchQualificationPlan(
            String effectiveQuery,
            String assistantMessage,
            List<String> suggestedReplies,
            AvailableFilter available,
            ConditionFilter condition,
            LocationFilter shipsTo,
            LocationsFilter shipsFrom,
            PriceFilter price,
            ReferenceFilter shops,
            ReferenceFilter categories,
            AttributesFilter attributes,
            RatingFilter rating,
            PriceTierFilter priceTier,
            List<DurableAttribute> durableAttributes
    ) {
        this(
                1,
                effectiveQuery,
                assistantMessage,
                suggestedReplies,
                List.of(),
                available,
                condition,
                shipsTo,
                shipsFrom,
                price,
                shops,
                categories,
                attributes,
                rating,
                priceTier,
                durableAttributes
        );
    }

    /** Backwards-compatible constructor for plans that predate durable product-search preferences. */
    public UserProductSearchQualificationPlan(
            String effectiveQuery,
            String assistantMessage,
            List<String> suggestedReplies,
            AvailableFilter available,
            ConditionFilter condition,
            LocationFilter shipsTo,
            LocationsFilter shipsFrom,
            PriceFilter price,
            ReferenceFilter shops,
            ReferenceFilter categories,
            AttributesFilter attributes,
            RatingFilter rating,
            PriceTierFilter priceTier
    ) {
        this(
                effectiveQuery,
                assistantMessage,
                suggestedReplies,
                available,
                condition,
                shipsTo,
                shipsFrom,
                price,
                shops,
                categories,
                attributes,
                rating,
                priceTier,
                List.of()
        );
    }

    public boolean currentSchema() {
        return schemaVersion == CURRENT_SCHEMA_VERSION;
    }

    public List<UserProductSearchFilterKind> missingFilters() {
        List<UserProductSearchFilterKind> missing = new ArrayList<>();
        for (UserProductSearchFilterKind kind : UserProductSearchFilterKind.values()) {
            if (state(kind) == UserProductSearchFilterState.MISSING) {
                missing.add(kind);
            }
        }
        return List.copyOf(missing);
    }

    public List<UserProductSearchQuestionTarget> missingTargets() {
        List<UserProductSearchQuestionTarget> missing = new ArrayList<>();
        addMissing(missing, condition.state(), UserProductSearchQuestionTarget.CONDITION);
        addMissing(missing, shipsTo.state(), UserProductSearchQuestionTarget.SHIPS_TO);
        addMissing(missing, shipsFrom.state(), UserProductSearchQuestionTarget.SHIPS_FROM);
        addMissing(missing, price.state(), UserProductSearchQuestionTarget.PRICE);
        attributes.values().forEach(attribute -> {
            if (attribute.state() == UserProductSearchFilterState.MISSING) {
                missing.add(questionTarget(attribute.name()));
            }
        });
        addMissing(missing, rating.state(), UserProductSearchQuestionTarget.RATING);
        addMissing(missing, priceTier.state(), UserProductSearchQuestionTarget.PRICE_TIER);
        return List.copyOf(missing);
    }

    /** Request-scoped dimensions the buyer explicitly cleared with an ANY decision. */
    public Set<UserProductSearchQuestionTarget> explicitAnyTargets() {
        Set<UserProductSearchQuestionTarget> targets = new LinkedHashSet<>();
        addExplicitAny(
                targets,
                condition.state(),
                condition.provenance(),
                UserProductSearchQuestionTarget.CONDITION
        );
        addExplicitAny(
                targets,
                shipsTo.state(),
                shipsTo.provenance(),
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        addExplicitAny(
                targets,
                shipsFrom.state(),
                shipsFrom.provenance(),
                UserProductSearchQuestionTarget.SHIPS_FROM
        );
        addExplicitAny(
                targets,
                price.state(),
                price.provenance(),
                UserProductSearchQuestionTarget.PRICE
        );
        attributes.values().forEach(attribute -> {
            if (isExplicitAny(attribute.state(), attribute.provenance())) {
                targets.add(questionTarget(attribute.name()));
            }
        });
        addExplicitAny(
                targets,
                rating.state(),
                rating.provenance(),
                UserProductSearchQuestionTarget.RATING
        );
        addExplicitAny(
                targets,
                priceTier.state(),
                priceTier.provenance(),
                UserProductSearchQuestionTarget.PRICE_TIER
        );
        return Set.copyOf(targets);
    }

    /**
     * Profile-derived dimensions that must not compete with an authoritative, buyer-grounded
     * request decision. This includes explicit ANY decisions and current request values.
     */
    public Set<UserProductSearchQuestionTarget> profileSuppressionTargets() {
        Set<UserProductSearchQuestionTarget> targets = new LinkedHashSet<>();
        addBuyerOverride(
                targets,
                condition.state(),
                condition.provenance(),
                UserProductSearchQuestionTarget.CONDITION
        );
        addBuyerOverride(
                targets,
                shipsTo.state(),
                shipsTo.provenance(),
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        addBuyerOverride(
                targets,
                shipsFrom.state(),
                shipsFrom.provenance(),
                UserProductSearchQuestionTarget.SHIPS_FROM
        );
        addBuyerOverride(
                targets,
                price.state(),
                price.provenance(),
                UserProductSearchQuestionTarget.PRICE
        );
        attributes.values().forEach(attribute -> {
            if (isBuyerOverride(attribute.state(), attribute.provenance())) {
                targets.add(questionTarget(attribute.name()));
            }
        });
        addBuyerOverride(
                targets,
                rating.state(),
                rating.provenance(),
                UserProductSearchQuestionTarget.RATING
        );
        addBuyerOverride(
                targets,
                priceTier.state(),
                priceTier.provenance(),
                UserProductSearchQuestionTarget.PRICE_TIER
        );
        return Set.copyOf(targets);
    }

    public UserProductSearchFilterState state(UserProductSearchFilterKind kind) {
        return switch (kind) {
            case AVAILABLE -> available.state();
            case CONDITION -> condition.state();
            case SHIPS_TO -> shipsTo.state();
            case SHIPS_FROM -> shipsFrom.state();
            case PRICE -> price.state();
            case SHOPS -> shops.state();
            case CATEGORIES -> categories.state();
            case ATTRIBUTES -> attributes.state();
            case RATING -> rating.state();
            case PRICE_TIER -> priceTier.state();
        };
    }

    public UserProductSearchQualificationPlan withConversation(
            String nextAssistantMessage,
            List<String> nextSuggestedReplies,
            List<UserProductSearchQuestionTarget> nextQuestionTargets
    ) {
        return new UserProductSearchQualificationPlan(
                schemaVersion,
                effectiveQuery,
                nextAssistantMessage,
                nextSuggestedReplies,
                nextQuestionTargets,
                available,
                condition,
                shipsTo,
                shipsFrom,
                price,
                shops,
                categories,
                attributes,
                rating,
                priceTier,
                durableAttributes
        );
    }

    private static void addMissing(
            List<UserProductSearchQuestionTarget> missing,
            UserProductSearchFilterState state,
            UserProductSearchQuestionTarget target
    ) {
        if (state == UserProductSearchFilterState.MISSING) {
            missing.add(target);
        }
    }

    private static void addExplicitAny(
            Set<UserProductSearchQuestionTarget> targets,
            UserProductSearchFilterState state,
            Provenance provenance,
            UserProductSearchQuestionTarget target
    ) {
        if (isExplicitAny(state, provenance)) {
            targets.add(target);
        }
    }

    private static boolean isExplicitAny(
            UserProductSearchFilterState state,
            Provenance provenance
    ) {
        return state == UserProductSearchFilterState.ANY && isBuyerGrounded(provenance);
    }

    private static void addBuyerOverride(
            Set<UserProductSearchQuestionTarget> targets,
            UserProductSearchFilterState state,
            Provenance provenance,
            UserProductSearchQuestionTarget target
    ) {
        if (isBuyerOverride(state, provenance)) {
            targets.add(target);
        }
    }

    private static boolean isBuyerOverride(
            UserProductSearchFilterState state,
            Provenance provenance
    ) {
        return (state == UserProductSearchFilterState.ANY
                || state == UserProductSearchFilterState.VALUE)
                && isBuyerGrounded(provenance);
    }

    private static boolean isBuyerGrounded(Provenance provenance) {
        if (provenance == null
                || provenance.evidence() == null
                || provenance.evidence().isBlank()) {
            return false;
        }
        return switch (provenance.source()) {
            case ORIGINAL_QUERY, CURRENT_USER_TURN, CONVERSATION -> true;
            case PROFILE, DURABLE_PREFERENCE, NONE, SYSTEM_POLICY -> false;
        };
    }

    private static UserProductSearchQuestionTarget questionTarget(UserProductSearchAttributeName name) {
        return switch (name) {
            case COLOR -> UserProductSearchQuestionTarget.COLOR;
            case SIZE -> UserProductSearchQuestionTarget.SIZE;
            case TARGET_GENDER -> UserProductSearchQuestionTarget.TARGET_GENDER;
        };
    }

    public record Provenance(UserProductSearchDecisionSource source, String evidence) {
        public Provenance {
            source = source == null ? UserProductSearchDecisionSource.NONE : source;
            evidence = optionalText(evidence);
        }

        public static Provenance none() {
            return new Provenance(UserProductSearchDecisionSource.NONE, null);
        }

        public static Provenance system(String evidence) {
            return new Provenance(UserProductSearchDecisionSource.SYSTEM_POLICY, evidence);
        }
    }

    public record AvailableFilter(
            UserProductSearchFilterState state,
            Boolean value,
            Provenance provenance
    ) {
        public AvailableFilter {
            Objects.requireNonNull(state, "Available filter state is required");
            provenance = provenance == null ? Provenance.none() : provenance;
        }

        public AvailableFilter(UserProductSearchFilterState state, Boolean value) {
            this(state, value, Provenance.none());
        }
    }

    public record ConditionFilter(
            UserProductSearchFilterState state,
            List<UserProductCondition> values,
            Provenance provenance
    ) {
        public ConditionFilter {
            Objects.requireNonNull(state, "Condition filter state is required");
            values = values == null ? List.of() : List.copyOf(values);
            provenance = provenance == null ? Provenance.none() : provenance;
        }

        public ConditionFilter(UserProductSearchFilterState state, List<UserProductCondition> values) {
            this(state, values, Provenance.none());
        }
    }

    public record Location(String country, String region, String postalCode) {
        public Location {
            country = requiredText(country, "Location country");
            region = optionalText(region);
            postalCode = optionalText(postalCode);
        }
    }

    public record LocationFilter(
            UserProductSearchFilterState state,
            Location value,
            Provenance provenance
    ) {
        public LocationFilter {
            Objects.requireNonNull(state, "Ships-to filter state is required");
            provenance = provenance == null ? Provenance.none() : provenance;
        }

        public LocationFilter(UserProductSearchFilterState state, Location value) {
            this(state, value, Provenance.none());
        }
    }

    public record LocationsFilter(
            UserProductSearchFilterState state,
            List<Location> values,
            Provenance provenance
    ) {
        public LocationsFilter {
            Objects.requireNonNull(state, "Ships-from filter state is required");
            values = values == null ? List.of() : List.copyOf(values);
            provenance = provenance == null ? Provenance.none() : provenance;
        }

        public LocationsFilter(UserProductSearchFilterState state, List<Location> values) {
            this(state, values, Provenance.none());
        }
    }

    public record PriceFilter(
            UserProductSearchFilterState state,
            Long minUsdMinor,
            Long maxUsdMinor,
            Provenance provenance
    ) {
        public PriceFilter {
            Objects.requireNonNull(state, "Price filter state is required");
            provenance = provenance == null ? Provenance.none() : provenance;
        }

        public PriceFilter(UserProductSearchFilterState state, Long minUsdMinor, Long maxUsdMinor) {
            this(state, minUsdMinor, maxUsdMinor, Provenance.none());
        }
    }

    /** Values are trusted provider IDs, never free-form model output. */
    public record ReferenceFilter(
            UserProductSearchFilterState state,
            List<String> values,
            Provenance provenance
    ) {
        public ReferenceFilter {
            Objects.requireNonNull(state, "Reference filter state is required");
            values = values == null ? List.of() : List.copyOf(values);
            provenance = provenance == null ? Provenance.none() : provenance;
        }

        public ReferenceFilter(UserProductSearchFilterState state, List<String> values) {
            this(state, values, Provenance.none());
        }
    }

    public record Attribute(
            UserProductSearchAttributeName name,
            UserProductSearchFilterState state,
            List<String> values,
            Provenance provenance
    ) {
        public Attribute {
            Objects.requireNonNull(name, "Attribute name is required");
            state = state == null ? UserProductSearchFilterState.VALUE : state;
            values = values == null ? List.of() : List.copyOf(values);
            provenance = provenance == null ? Provenance.none() : provenance;
        }

        public Attribute(UserProductSearchAttributeName name, List<String> values) {
            this(name, UserProductSearchFilterState.VALUE, values, Provenance.none());
        }
    }

    public record AttributesFilter(UserProductSearchFilterState state, List<Attribute> values) {
        public AttributesFilter {
            Objects.requireNonNull(state, "Attributes filter state is required");
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    /** A newly learned stable attribute to persist for reuse in the same product scope. */
    public record DurableAttribute(
            String scope,
            UserProductSearchAttributeName name,
            List<String> values
    ) {
        public DurableAttribute {
            scope = requiredText(scope, "Durable attribute scope");
            Objects.requireNonNull(name, "Durable attribute name is required");
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record RatingFilter(
            UserProductSearchFilterState state,
            BigDecimal min,
            Long minCount,
            Provenance provenance
    ) {
        public RatingFilter {
            Objects.requireNonNull(state, "Rating filter state is required");
            provenance = provenance == null ? Provenance.none() : provenance;
        }

        public RatingFilter(UserProductSearchFilterState state, BigDecimal min, Long minCount) {
            this(state, min, minCount, Provenance.none());
        }
    }

    public record PriceTierFilter(
            UserProductSearchFilterState state,
            List<UserProductPriceTier> values,
            Provenance provenance
    ) {
        public PriceTierFilter {
            Objects.requireNonNull(state, "Price-tier filter state is required");
            values = values == null ? List.of() : List.copyOf(values);
            provenance = provenance == null ? Provenance.none() : provenance;
        }

        public PriceTierFilter(UserProductSearchFilterState state, List<UserProductPriceTier> values) {
            this(state, values, Provenance.none());
        }
    }

    private static String requiredText(String value, String label) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
