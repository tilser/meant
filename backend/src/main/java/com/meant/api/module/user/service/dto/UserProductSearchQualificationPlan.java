package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchFilterKind;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Server-owned, provider-neutral filter plan produced before catalog discovery. */
public record UserProductSearchQualificationPlan(
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

    public UserProductSearchQualificationPlan {
        effectiveQuery = requiredText(effectiveQuery, "Effective query");
        assistantMessage = requiredText(assistantMessage, "Assistant message");
        suggestedReplies = suggestedReplies == null ? List.of() : List.copyOf(suggestedReplies);
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

    public List<UserProductSearchFilterKind> missingFilters() {
        return List.of(UserProductSearchFilterKind.values()).stream()
                .filter(kind -> state(kind) == UserProductSearchFilterState.MISSING)
                .toList();
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

    public record AvailableFilter(UserProductSearchFilterState state, Boolean value) {
        public AvailableFilter {
            Objects.requireNonNull(state, "Available filter state is required");
        }
    }

    public record ConditionFilter(UserProductSearchFilterState state, List<UserProductCondition> values) {
        public ConditionFilter {
            Objects.requireNonNull(state, "Condition filter state is required");
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record Location(String country, String region, String postalCode) {
        public Location {
            country = requiredText(country, "Location country");
            region = optionalText(region);
            postalCode = optionalText(postalCode);
        }
    }

    public record LocationFilter(UserProductSearchFilterState state, Location value) {
        public LocationFilter {
            Objects.requireNonNull(state, "Ships-to filter state is required");
        }
    }

    public record LocationsFilter(UserProductSearchFilterState state, List<Location> values) {
        public LocationsFilter {
            Objects.requireNonNull(state, "Ships-from filter state is required");
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record PriceFilter(UserProductSearchFilterState state, Long minUsdMinor, Long maxUsdMinor) {
        public PriceFilter {
            Objects.requireNonNull(state, "Price filter state is required");
        }
    }

    /** Values are trusted provider IDs, never free-form model output. */
    public record ReferenceFilter(UserProductSearchFilterState state, List<String> values) {
        public ReferenceFilter {
            Objects.requireNonNull(state, "Reference filter state is required");
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    public record Attribute(UserProductSearchAttributeName name, List<String> values) {
        public Attribute {
            Objects.requireNonNull(name, "Attribute name is required");
            values = values == null ? List.of() : List.copyOf(values);
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

    public record RatingFilter(UserProductSearchFilterState state, BigDecimal min, Long minCount) {
        public RatingFilter {
            Objects.requireNonNull(state, "Rating filter state is required");
        }
    }

    public record PriceTierFilter(UserProductSearchFilterState state, List<UserProductPriceTier> values) {
        public PriceTierFilter {
            Objects.requireNonNull(state, "Price-tier filter state is required");
            values = values == null ? List.of() : List.copyOf(values);
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
