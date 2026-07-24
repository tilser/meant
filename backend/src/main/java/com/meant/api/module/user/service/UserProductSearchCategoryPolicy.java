package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Server-owned category policy for critical search gaps and purpose-limited provider context.
 *
 * <p>The LLM still performs the general relevance assessment. This policy enforces the small set of
 * safety-critical category invariants that must not depend on prompt compliance or model availability.</p>
 */
@Component
public class UserProductSearchCategoryPolicy {

    private static final Pattern DIGITAL = words(
            "digital", "download", "ebook", "e-book", "pdf", "software", "license", "online course",
            "audiobook", "game code", "gift card", "subscription code");
    private static final Pattern FOOD = words(
            "food", "snack", "coffee", "tea", "chocolate", "pasta", "bread", "cereal", "meal",
            "drink", "beverage", "sauce", "spice", "protein powder", "supplement", "ingredients");
    private static final Pattern FIT_SENSITIVE_FOOTWEAR = words(
            "football boots", "soccer boots", "cleats", "boots");
    private static final Pattern APPAREL = words(
            "shirt", "t-shirt", "dress", "pants", "trousers", "jeans", "jacket", "coat", "sweater",
            "hoodie", "clothing", "apparel", "shorts", "skirt", "socks", "shoes", "sneakers",
            "trainers", "footwear", "sandals");
    private static final Pattern TECHNOLOGY = words(
            "laptop", "computer", "phone", "tablet", "headphones", "earbuds", "charger", "cable",
            "monitor", "keyboard", "mouse", "camera", "router", "smartwatch", "electronics");
    private static final Pattern PERSONAL_CARE = words(
            "skincare", "skin care", "shampoo", "conditioner", "sunscreen", "moisturizer", "cosmetic",
            "makeup", "deodorant", "soap", "fragrance", "personal care");
    private static final Pattern HOME = words(
            "furniture", "sofa", "chair", "table", "lamp", "bedding", "mattress", "cookware",
            "kitchen", "vacuum", "appliance", "home", "pillow", "rug", "curtain");
    private static final Pattern UNVERIFIED_HARD_CONSTRAINT = Pattern.compile(
            "(?i)(?:\\$|\\b(?:usd|under|below|over|above|between|size|rated?|stars?|reviews?|"
                    + "new|used|secondhand|preowned|price tier|ships?\\s+(?:to|from)|deliver(?:y|ed)?\\s+to)\\b)"
    );

    public UserProductSearchQualificationPlan enforce(
            UserProductSearchQualificationPlan plan,
            GenerateUserProductSearchQualificationQuery query
    ) {
        Category category = category(query.originalQuery(), query.message());
        var shipsTo = plan.shipsTo();
        var shipsFrom = plan.shipsFrom();
        EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> attributes =
                attributes(plan.attributes());
        boolean changed = false;

        if (category == Category.FIT_SENSITIVE_FOOTWEAR) {
            var size = attributes.get(UserProductSearchAttributeName.SIZE);
            if (!resolved(size.state())) {
                UserProductSearchQualificationPlan.Attribute durableSize = durableSize(query);
                attributes.put(
                        UserProductSearchAttributeName.SIZE,
                        durableSize == null ? missingAttribute(UserProductSearchAttributeName.SIZE) : durableSize
                );
                changed = true;
            }
            if (!resolved(shipsTo.state())) {
                UserProductSearchQualificationPlan.LocationFilter savedDestination = savedDestination(query.settings());
                shipsTo = savedDestination == null ? missingLocation() : savedDestination;
                changed = true;
            }
        } else if (category == Category.FOOD) {
            changed |= putNotApplicable(attributes, UserProductSearchAttributeName.SIZE);
            changed |= putNotApplicable(attributes, UserProductSearchAttributeName.TARGET_GENDER);
        } else if (category == Category.DIGITAL) {
            changed |= putNotApplicable(attributes, UserProductSearchAttributeName.SIZE);
            changed |= putNotApplicable(attributes, UserProductSearchAttributeName.TARGET_GENDER);
            if (shipsTo.state() != UserProductSearchFilterState.NOT_APPLICABLE) {
                shipsTo = notApplicableLocation();
                changed = true;
            }
            if (shipsFrom.state() != UserProductSearchFilterState.NOT_APPLICABLE) {
                shipsFrom = new UserProductSearchQualificationPlan.LocationsFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE,
                        List.of(),
                        UserProductSearchQualificationPlan.Provenance.none()
                );
                changed = true;
            }
        }

        if (!changed) {
            return plan;
        }
        UserProductSearchQualificationPlan adjusted = new UserProductSearchQualificationPlan(
                plan.schemaVersion(),
                plan.effectiveQuery(),
                plan.assistantMessage(),
                plan.suggestedReplies(),
                plan.questionTargets(),
                plan.available(),
                plan.condition(),
                shipsTo,
                shipsFrom,
                plan.price(),
                plan.shops(),
                plan.categories(),
                new UserProductSearchQualificationPlan.AttributesFilter(attributeState(attributes), attributes.values()
                        .stream().toList()),
                plan.rating(),
                plan.priceTier(),
                plan.durableAttributes()
        );
        List<UserProductSearchQuestionTarget> missing = adjusted.missingTargets();
        if (missing.isEmpty()) {
            return adjusted.withConversation("I have everything I need to search.", List.of(), List.of());
        }
        return adjusted.withConversation(question(missing), List.of(), missing);
    }

    public Category category(String originalQuery, String latestTurn) {
        Category latest = explicitCategory(latestTurn);
        return latest == Category.OTHER ? explicitCategory(originalQuery) : latest;
    }

    public boolean permitsConservativeFallback(GenerateUserProductSearchQualificationQuery query) {
        Category category = category(query.originalQuery(), query.message());
        return (category == Category.FIT_SENSITIVE_FOOTWEAR
                || category == Category.FOOD
                || category == Category.DIGITAL)
                && !hasUnverifiedHardConstraint(query.originalQuery())
                && !hasUnverifiedHardConstraint(query.message());
    }

    public List<ShoppingFilterResult> providerContextFilters(
            String query,
            List<ShoppingFilterResult> filters
    ) {
        Set<String> allowed = new LinkedHashSet<>(List.of("shopping", "sustainability"));
        switch (category(query, query)) {
            case FIT_SENSITIVE_FOOTWEAR, APPAREL -> allowed.add("materials");
            case FOOD -> allowed.add("food");
            case DIGITAL, TECHNOLOGY -> allowed.add("technology");
            case PERSONAL_CARE -> allowed.add("personal-care");
            case HOME -> {
                allowed.add("home");
                allowed.add("materials");
            }
            case OTHER -> {
                // Only generally applicable, non-sensitive preferences are sent upstream.
            }
        }
        return filters == null ? List.of() : filters.stream()
                .filter(filter -> filter != null && filter.category() != null)
                .filter(filter -> allowed.contains(filter.category().trim().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private Category explicitCategory(String value) {
        String normalized = normalize(value);
        if (DIGITAL.matcher(normalized).find()) {
            return Category.DIGITAL;
        }
        if (FOOD.matcher(normalized).find()) {
            return Category.FOOD;
        }
        if (FIT_SENSITIVE_FOOTWEAR.matcher(normalized).find()) {
            return Category.FIT_SENSITIVE_FOOTWEAR;
        }
        if (APPAREL.matcher(normalized).find()) {
            return Category.APPAREL;
        }
        if (TECHNOLOGY.matcher(normalized).find()) {
            return Category.TECHNOLOGY;
        }
        if (PERSONAL_CARE.matcher(normalized).find()) {
            return Category.PERSONAL_CARE;
        }
        if (HOME.matcher(normalized).find()) {
            return Category.HOME;
        }
        return Category.OTHER;
    }

    private boolean hasUnverifiedHardConstraint(String value) {
        return value != null && UNVERIFIED_HARD_CONSTRAINT.matcher(value).find();
    }

    private UserProductSearchQualificationPlan.Attribute durableSize(
            GenerateUserProductSearchQualificationQuery query
    ) {
        String searchText = normalize(query.originalQuery() + " " + query.message());
        return query.durablePreferences().stream()
                .filter(preference -> preference.attributeName() == UserProductSearchAttributeName.SIZE)
                .filter(preference -> scopeMatches(preference.scope(), searchText))
                .filter(preference -> !preference.values().isEmpty())
                .findFirst()
                .map(preference -> new UserProductSearchQualificationPlan.Attribute(
                        UserProductSearchAttributeName.SIZE,
                        UserProductSearchFilterState.VALUE,
                        preference.values(),
                        new UserProductSearchQualificationPlan.Provenance(
                                UserProductSearchDecisionSource.DURABLE_PREFERENCE,
                                preference.scope() + " SIZE " + String.join(" ", preference.values())
                        )
                ))
                .orElse(null);
    }

    private boolean scopeMatches(String scope, String normalizedQuery) {
        String normalizedScope = normalize(scope).replace('-', ' ');
        if (normalizedScope.isBlank()) {
            return false;
        }
        return java.util.Arrays.stream(normalizedScope.split("\\s+"))
                .filter(token -> !token.isBlank())
                .allMatch(token -> containsToken(normalizedQuery, token));
    }

    private boolean containsToken(String value, String token) {
        return (" " + value + " ").contains(" " + token + " ");
    }

    private UserProductSearchQualificationPlan.LocationFilter savedDestination(UserSettingsResult settings) {
        if (settings == null || settings.location() == null) {
            return null;
        }
        var location = settings.location();
        String country = location.code() == null ? location.country() : location.code();
        if (country == null || country.isBlank()) {
            return null;
        }
        return new UserProductSearchQualificationPlan.LocationFilter(
                UserProductSearchFilterState.VALUE,
                new UserProductSearchQualificationPlan.Location(
                        country,
                        location.region(),
                        location.postalCode()
                ),
                new UserProductSearchQualificationPlan.Provenance(
                        UserProductSearchDecisionSource.PROFILE,
                        country
                )
        );
    }

    private EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> attributes(
            UserProductSearchQualificationPlan.AttributesFilter filter
    ) {
        EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> values =
                new EnumMap<>(UserProductSearchAttributeName.class);
        filter.values().forEach(attribute -> values.put(attribute.name(), attribute));
        for (UserProductSearchAttributeName name : UserProductSearchAttributeName.values()) {
            values.putIfAbsent(name, notApplicableAttribute(name));
        }
        return values;
    }

    private boolean putNotApplicable(
            EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> attributes,
            UserProductSearchAttributeName name
    ) {
        if (attributes.get(name).state() == UserProductSearchFilterState.NOT_APPLICABLE) {
            return false;
        }
        attributes.put(name, notApplicableAttribute(name));
        return true;
    }

    private UserProductSearchFilterState attributeState(
            EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> attributes
    ) {
        if (attributes.values().stream().anyMatch(value -> value.state() == UserProductSearchFilterState.MISSING)) {
            return UserProductSearchFilterState.MISSING;
        }
        if (attributes.values().stream().anyMatch(value -> value.state() == UserProductSearchFilterState.VALUE)) {
            return UserProductSearchFilterState.VALUE;
        }
        if (attributes.values().stream().anyMatch(value -> value.state() == UserProductSearchFilterState.ANY)) {
            return UserProductSearchFilterState.ANY;
        }
        return UserProductSearchFilterState.NOT_APPLICABLE;
    }

    private UserProductSearchQualificationPlan.Attribute missingAttribute(UserProductSearchAttributeName name) {
        return new UserProductSearchQualificationPlan.Attribute(
                name,
                UserProductSearchFilterState.MISSING,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.Attribute notApplicableAttribute(UserProductSearchAttributeName name) {
        return new UserProductSearchQualificationPlan.Attribute(
                name,
                UserProductSearchFilterState.NOT_APPLICABLE,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.LocationFilter missingLocation() {
        return new UserProductSearchQualificationPlan.LocationFilter(
                UserProductSearchFilterState.MISSING,
                null,
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.LocationFilter notApplicableLocation() {
        return new UserProductSearchQualificationPlan.LocationFilter(
                UserProductSearchFilterState.NOT_APPLICABLE,
                null,
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private boolean resolved(UserProductSearchFilterState state) {
        return state == UserProductSearchFilterState.VALUE || state == UserProductSearchFilterState.ANY;
    }

    private String question(List<UserProductSearchQuestionTarget> missing) {
        boolean size = missing.contains(UserProductSearchQuestionTarget.SIZE);
        boolean destination = missing.contains(UserProductSearchQuestionTarget.SHIPS_TO);
        if (size && destination && missing.size() == 2) {
            return "What boot size do you need, and what country or postal code should they ship to?";
        }
        if (size && missing.size() == 1) {
            return "What boot size do you need?";
        }
        if (destination && missing.size() == 1) {
            return "What country or postal code should the order ship to?";
        }
        List<String> labels = new ArrayList<>();
        missing.forEach(target -> labels.add(target.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        return "Please provide " + String.join(", ", labels) + " before I search.";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9$]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static Pattern words(String... values) {
        String alternatives = java.util.Arrays.stream(values)
                .map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow();
        return Pattern.compile("(?:^|\\s)(?:" + alternatives + ")(?:$|\\s)");
    }

    public enum Category {
        FIT_SENSITIVE_FOOTWEAR,
        FOOD,
        DIGITAL,
        APPAREL,
        TECHNOLOGY,
        PERSONAL_CARE,
        HOME,
        OTHER
    }
}
