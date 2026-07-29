package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserProductSearchConversationMessage;
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
import java.util.Optional;
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
            "download", "digital download", "digital guide", "ebook", "e-book", "pdf download",
            "software download", "software license", "license key", "online course", "audiobook",
            "game code", "digital gift card", "e-gift card", "subscription code");
    private static final Pattern EXPLICIT_DIGITAL_PRODUCT = Pattern.compile(
            "(?:^|\\s)digital(?:\\s+[a-z0-9]+){0,5}\\s+(?:book|course|download|guide)(?:$|\\s)");
    private static final Pattern PHYSICAL_EBOOK_READER = words(
            "ebook reader", "e-book reader", "e reader", "e-reader", "ereader");
    private static final Pattern NON_WEARABLE_FOOTWEAR_ACCESSORY = words(
            "shoe rack", "shoe cleaner", "shoe cleaning kit", "shoe horn", "shoe tree", "shoe trees",
            "shoe laces", "shoelace", "shoelaces", "boot rack", "boot cleaner", "boot dryer", "boot bag");
    private static final Pattern FOOD = words(
            "food", "snack", "coffee", "tea", "chocolate", "pasta", "bread", "cereal", "meal",
            "drink", "beverage", "sauce", "spice", "protein powder", "supplement", "ingredients");
    private static final Pattern FOOTWEAR = words(
            "shoe", "shoes", "running shoe", "running shoes", "sneaker", "sneakers", "trainer", "trainers",
            "footwear", "sandal", "sandals", "boot", "boots", "football boot", "football boots",
            "soccer boot", "soccer boots", "cleat", "cleats");
    private static final Pattern APPAREL = words(
            "shirt", "t-shirt", "t shirt", "blouse", "dress", "pants", "trousers", "jeans", "jacket", "coat",
            "sweater", "hoodie", "clothing", "apparel", "shorts", "skirt", "sock", "socks", "suit", "blazer",
            "bra", "underwear", "swimwear", "jersey");
    private static final Pattern TECHNOLOGY = words(
            "laptop", "computer", "phone", "tablet", "headphones", "earbuds", "charger", "cable",
            "monitor", "keyboard", "mouse", "camera", "router", "smartwatch", "electronics",
            "ebook reader", "e-book reader", "e reader", "e-reader", "ereader");
    private static final Pattern PERSONAL_CARE = words(
            "skincare", "skin care", "shampoo", "conditioner", "sunscreen", "moisturizer", "cosmetic",
            "makeup", "deodorant", "soap", "fragrance", "personal care");
    private static final Pattern HOME = words(
            "furniture", "sofa", "chair", "table", "lamp", "bedding", "mattress", "cookware",
            "kitchen", "vacuum", "appliance", "home", "pillow", "rug", "curtain");
    private static final Pattern CONDITION_CONSTRAINT = Pattern.compile(
            "(?i)\\b(?:condition|brand\\s+new|new\\s+condition|used|secondhand|second-hand"
                    + "|preowned|pre-owned)\\b"
                    + "|\\bnew\\b(?!\\s+(?:balance|era|york|zealand)\\b)");
    private static final Pattern SHIPS_TO_CONSTRAINT = Pattern.compile(
            "(?i)\\b(?:ships?|shipped|shipping|deliver|delivered|delivery)\\s+(?:it\\s+)?to\\b"
                    + "|\\b(?:based|located)\\s+in\\b|\\bi\\s+(?:am|live)\\s+in\\b|\\bi['’]m\\s+in\\b"
                    + "|\\b(?:shipping\\s+destination|delivery\\s+location|shipping\\s+location"
                    + "|destination|location|country)\\s+(?:doesn['’]?t|doesnt|does\\s+not)\\s+matter\\b");
    private static final Pattern SHIPS_FROM_CONSTRAINT = Pattern.compile(
            "(?i)\\b(?:ships?|shipped|shipping)\\s+from\\b|\\bmade\\s+in\\b"
                    + "|\\borigin(?:ating)?\\s+from\\b|\\b(?:shipping\\s+origin|source\\s+country|origin)"
                    + "\\s+(?:doesn['’]?t|doesnt|does\\s+not)\\s+matter\\b");
    private static final Pattern PRICE_CONSTRAINT = Pattern.compile(
            "(?i)(?:\\$|\\b(?:usd|under|below|over|above|between)\\b)");
    private static final Pattern COLOR_CONSTRAINT = words(
            "color", "colour", "black", "white", "red", "blue", "green", "brown", "grey", "gray", "pink",
            "purple", "orange", "yellow", "beige", "navy", "teal", "gold", "silver");
    private static final Pattern SIZE_CONSTRAINT = Pattern.compile(
            "(?i)\\b(?:size|sizing|xxs|xs|xl|xxl|xxxl)\\b");
    private static final Pattern TARGET_GENDER_CONSTRAINT = words(
            "men", "men's", "mens", "women", "women's", "womens", "male", "female", "unisex", "boy", "boys",
            "girl", "girls");
    private static final Pattern RATING_CONSTRAINT = words(
            "rated", "rating", "star", "stars", "review", "reviews");
    private static final Pattern PRICE_TIER_CONSTRAINT = Pattern.compile(
            "(?i)\\b(?:(?:low|medium|high)\\s+(?:relative\\s+)?price(?:\\s+tier)?"
                    + "|price\\s+tier)\\b");
    private static final Set<String> PRODUCT_SUBJECT_IGNORED_WORDS = Set.of(
            "a", "actually", "an", "any", "buy", "care", "could", "different", "do", "doesn", "doesnt",
            "don", "dont", "either", "find", "get", "have", "i", "im", "item", "items", "like", "look",
            "looking", "m", "matter", "me", "my", "need", "neither", "new", "no", "none", "not", "of",
            "pack", "pair", "please", "preference", "product", "products", "rather", "search", "searching",
            "set", "shop", "shopping", "show", "some", "something", "switch", "t", "than", "the", "want",
            "whatever", "would"
    );
    private static final Set<String> PRODUCT_SUBJECT_BOUNDARY_WORDS = Set.of(
            "above", "below", "between", "budget", "deliver", "delivered", "delivery", "destination",
            "for", "from", "in", "made", "on", "over", "rated", "rating", "review", "reviews", "ship",
            "shipped", "shipping", "ships", "size", "sizing", "star", "stars", "to", "under", "with"
    );
    private static final Set<String> PRODUCT_SUBJECT_HARD_CONSTRAINT_WORDS = Set.of(
            "above", "below", "between", "budget", "deliver", "delivered", "delivery", "destination",
            "over", "rated", "rating", "review", "reviews", "ship", "shipped", "shipping", "ships",
            "size", "sizing", "star", "stars", "under"
    );

    public UserProductSearchQualificationPlan enforce(
            UserProductSearchQualificationPlan plan,
            GenerateUserProductSearchQualificationQuery query
    ) {
        Category category = category(query);
        var shipsTo = plan.shipsTo();
        var shipsFrom = plan.shipsFrom();
        EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> attributes =
                attributes(plan.attributes());
        Set<UserProductSearchQuestionTarget> explicitHardConstraints =
                new LinkedHashSet<>(unverifiedHardConstraintTargets(query));
        boolean changed = false;

        if (category != Category.DIGITAL
                && (!resolved(shipsTo.state())
                        || explicitlyOverridesSavedDecision(
                                explicitHardConstraints,
                                UserProductSearchQuestionTarget.SHIPS_TO,
                                shipsTo.provenance().source(),
                                UserProductSearchDecisionSource.PROFILE))) {
            shipsTo = explicitHardConstraints.contains(UserProductSearchQuestionTarget.SHIPS_TO)
                    ? missingLocation()
                    : savedOrMissingDestination(query.settings());
            changed = true;
        }

        if (category == Category.FOOTWEAR || category == Category.APPAREL) {
            var size = attributes.get(UserProductSearchAttributeName.SIZE);
            if (!resolved(size.state())
                    || explicitlyOverridesSavedDecision(
                            explicitHardConstraints,
                            UserProductSearchQuestionTarget.SIZE,
                            size.provenance().source(),
                            UserProductSearchDecisionSource.DURABLE_PREFERENCE)) {
                attributes.put(
                        UserProductSearchAttributeName.SIZE,
                        explicitHardConstraints.contains(UserProductSearchQuestionTarget.SIZE)
                                ? missingAttribute(UserProductSearchAttributeName.SIZE)
                                : durableOrMissingSize(query)
                );
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
        return adjusted.withConversation(question(missing, category), List.of(), missing);
    }

    /**
     * Preserves user-stated hard constraints when no model result can be validated.
     *
     * <p>The deterministic detector is intentionally used only for the degraded path: words such
     * as {@code new}, {@code under}, or {@code orange} can be brand or product terms in normal
     * language, so the healthy LLM path remains responsible for their semantics. During an outage,
     * asking for confirmation is safer than silently dropping them.</p>
     */
    public UserProductSearchQualificationPlan enforceConservativeFallback(
            UserProductSearchQualificationPlan plan,
            GenerateUserProductSearchQualificationQuery query
    ) {
        var condition = plan.condition();
        var shipsTo = plan.shipsTo();
        var shipsFrom = plan.shipsFrom();
        var price = plan.price();
        var rating = plan.rating();
        var priceTier = plan.priceTier();
        EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> attributes =
                attributes(plan.attributes());

        for (UserProductSearchQuestionTarget target : unverifiedHardConstraintTargets(query)) {
            switch (target) {
                case CONDITION -> {
                    if (unresolvedButNotMissing(condition.state())) {
                        condition = missingCondition();
                    }
                }
                case SHIPS_TO -> {
                    if (unresolvedButNotMissing(shipsTo.state())) {
                        shipsTo = missingLocation();
                    }
                }
                case SHIPS_FROM -> {
                    if (unresolvedButNotMissing(shipsFrom.state())) {
                        shipsFrom = missingLocations();
                    }
                }
                case PRICE -> {
                    if (unresolvedButNotMissing(price.state())) {
                        price = missingPrice();
                    }
                }
                case COLOR, SIZE, TARGET_GENDER -> {
                    UserProductSearchAttributeName name = attributeName(target);
                    if (unresolvedButNotMissing(attributes.get(name).state())) {
                        attributes.put(name, missingAttribute(name));
                    }
                }
                case RATING -> {
                    if (unresolvedButNotMissing(rating.state())) {
                        rating = missingRating();
                    }
                }
                case PRICE_TIER -> {
                    if (unresolvedButNotMissing(priceTier.state())) {
                        priceTier = missingPriceTier();
                    }
                }
            }
        }

        UserProductSearchQualificationPlan constrained = new UserProductSearchQualificationPlan(
                plan.schemaVersion(),
                plan.effectiveQuery(),
                plan.assistantMessage(),
                plan.suggestedReplies(),
                plan.questionTargets(),
                plan.available(),
                condition,
                shipsTo,
                shipsFrom,
                price,
                plan.shops(),
                plan.categories(),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        attributeState(attributes),
                        attributes.values().stream().toList()
                ),
                rating,
                priceTier,
                plan.durableAttributes()
        );
        return enforce(constrained, query);
    }

    public Category category(String originalQuery, String latestTurn) {
        Category latest = explicitCategory(latestTurn);
        return latest == Category.OTHER ? explicitCategory(originalQuery) : latest;
    }

    /**
     * Extracts the grammatical product head without relying on a finite product vocabulary.
     *
     * <p>Request boilerplate and trailing hard-constraint clauses are discarded, then the final
     * remaining content word is used as the product head. Keeping the full retained phrase lets
     * callers distinguish a real product phrase from a one-word answer.</p>
     */
    public Optional<ProductSubject> productSubject(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        List<String> terms = new ArrayList<>();
        List<String> tokens = List.of(normalized.split(" "));
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if ("new".equals(token)
                    && index + 1 < tokens.size()
                    && "balance".equals(tokens.get(index + 1))) {
                terms.add(token);
                continue;
            }
            if (PRODUCT_SUBJECT_BOUNDARY_WORDS.contains(token)) {
                if (!terms.isEmpty() || PRODUCT_SUBJECT_HARD_CONSTRAINT_WORDS.contains(token)) {
                    break;
                }
                continue;
            }
            if (PRODUCT_SUBJECT_IGNORED_WORDS.contains(token)
                    || token.matches("(?:\\$|\\d).*")) {
                continue;
            }
            terms.add(token);
        }
        if (terms.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ProductSubject(productWordStem(terms.getLast()), terms));
    }

    private Category category(GenerateUserProductSearchQualificationQuery query) {
        Category trustedReference = explicitCategory(query.trustedReferenceProductText());
        if (trustedReference != Category.OTHER) {
            return trustedReference;
        }
        Category direct = category(query.originalQuery(), query.message());
        if (direct != Category.OTHER) {
            return direct;
        }
        for (int index = query.conversation().size() - 1; index >= 0; index--) {
            UserProductSearchConversationMessage message = query.conversation().get(index);
            if (message.role() != UserProductSearchConversationMessage.Role.USER) {
                continue;
            }
            Category prior = explicitCategory(message.text());
            if (prior != Category.OTHER) {
                return prior;
            }
        }
        return Category.OTHER;
    }

    public boolean permitsConservativeFallback(GenerateUserProductSearchQualificationQuery query) {
        return true;
    }

    public String conservativeFallbackDenialReason(GenerateUserProductSearchQualificationQuery query) {
        return null;
    }

    /**
     * Returns hard-filter decisions mentioned in user text that a model-free fallback must ask the
     * user to restate or confirm instead of silently dropping.
     */
    public List<UserProductSearchQuestionTarget> unverifiedHardConstraintTargets(
            GenerateUserProductSearchQualificationQuery query
    ) {
        String text = normalize(query.originalQuery() + " " + query.message());
        LinkedHashSet<UserProductSearchQuestionTarget> targets = new LinkedHashSet<>();
        addIfMatches(targets, CONDITION_CONSTRAINT, text, UserProductSearchQuestionTarget.CONDITION);
        addIfMatches(targets, SHIPS_TO_CONSTRAINT, text, UserProductSearchQuestionTarget.SHIPS_TO);
        addIfMatches(targets, SHIPS_FROM_CONSTRAINT, text, UserProductSearchQuestionTarget.SHIPS_FROM);
        addIfMatches(targets, PRICE_CONSTRAINT, text, UserProductSearchQuestionTarget.PRICE);
        addIfMatches(targets, COLOR_CONSTRAINT, text, UserProductSearchQuestionTarget.COLOR);
        addIfMatches(targets, SIZE_CONSTRAINT, text, UserProductSearchQuestionTarget.SIZE);
        addIfMatches(targets, TARGET_GENDER_CONSTRAINT, text, UserProductSearchQuestionTarget.TARGET_GENDER);
        addIfMatches(targets, RATING_CONSTRAINT, text, UserProductSearchQuestionTarget.RATING);
        addIfMatches(targets, PRICE_TIER_CONSTRAINT, text, UserProductSearchQuestionTarget.PRICE_TIER);
        return List.copyOf(targets);
    }

    public List<ShoppingFilterResult> providerContextFilters(
            String query,
            List<ShoppingFilterResult> filters
    ) {
        Set<String> allowed = new LinkedHashSet<>(List.of("shopping", "sustainability"));
        switch (category(query, query)) {
            case FOOTWEAR, APPAREL -> allowed.add("materials");
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
        if (PHYSICAL_EBOOK_READER.matcher(normalized).find()) {
            return Category.TECHNOLOGY;
        }
        if (NON_WEARABLE_FOOTWEAR_ACCESSORY.matcher(normalized).find()) {
            return Category.OTHER;
        }
        if (EXPLICIT_DIGITAL_PRODUCT.matcher(normalized).find()) {
            return Category.DIGITAL;
        }
        List<Category> matches = new ArrayList<>();
        addCategoryIfMatches(matches, DIGITAL, normalized, Category.DIGITAL);
        addCategoryIfMatches(matches, FOOD, normalized, Category.FOOD);
        addCategoryIfMatches(matches, FOOTWEAR, normalized, Category.FOOTWEAR);
        addCategoryIfMatches(matches, APPAREL, normalized, Category.APPAREL);
        addCategoryIfMatches(matches, TECHNOLOGY, normalized, Category.TECHNOLOGY);
        addCategoryIfMatches(matches, PERSONAL_CARE, normalized, Category.PERSONAL_CARE);
        addCategoryIfMatches(matches, HOME, normalized, Category.HOME);
        return matches.size() == 1 ? matches.getFirst() : Category.OTHER;
    }

    private void addCategoryIfMatches(
            List<Category> matches,
            Pattern pattern,
            String value,
            Category category
    ) {
        if (pattern.matcher(value).find()) {
            matches.add(category);
        }
    }

    private void addIfMatches(
            Set<UserProductSearchQuestionTarget> targets,
            Pattern pattern,
            String text,
            UserProductSearchQuestionTarget target
    ) {
        if (pattern.matcher(text).find()) {
            targets.add(target);
        }
    }

    private UserProductSearchQualificationPlan.Attribute durableSize(
            GenerateUserProductSearchQualificationQuery query
    ) {
        String searchText = query.trustedReferenceProductText() == null
                ? normalize(query.originalQuery() + " " + query.message())
                : normalize(query.trustedReferenceProductText());
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

    private UserProductSearchQualificationPlan.Attribute durableOrMissingSize(
            GenerateUserProductSearchQualificationQuery query
    ) {
        UserProductSearchQualificationPlan.Attribute durableSize = durableSize(query);
        return durableSize == null ? missingAttribute(UserProductSearchAttributeName.SIZE) : durableSize;
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

    private UserProductSearchQualificationPlan.LocationFilter savedOrMissingDestination(
            UserSettingsResult settings
    ) {
        UserProductSearchQualificationPlan.LocationFilter savedDestination = savedDestination(settings);
        return savedDestination == null ? missingLocation() : savedDestination;
    }

    private boolean explicitlyOverridesSavedDecision(
            Set<UserProductSearchQuestionTarget> explicitHardConstraints,
            UserProductSearchQuestionTarget target,
            UserProductSearchDecisionSource actualSource,
            UserProductSearchDecisionSource savedSource
    ) {
        return explicitHardConstraints.contains(target) && actualSource == savedSource;
    }

    private String productWordStem(String value) {
        if (value.endsWith("ies") && value.length() > 4) {
            return value.substring(0, value.length() - 3) + "y";
        }
        if (value.endsWith("s") && !value.endsWith("ss") && value.length() > 3) {
            return value.substring(0, value.length() - 1);
        }
        return value;
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

    private boolean unresolvedButNotMissing(UserProductSearchFilterState state) {
        return !resolved(state) && state != UserProductSearchFilterState.MISSING;
    }

    private UserProductSearchAttributeName attributeName(UserProductSearchQuestionTarget target) {
        return switch (target) {
            case COLOR -> UserProductSearchAttributeName.COLOR;
            case SIZE -> UserProductSearchAttributeName.SIZE;
            case TARGET_GENDER -> UserProductSearchAttributeName.TARGET_GENDER;
            default -> throw new IllegalArgumentException("Target is not a product-search attribute: " + target);
        };
    }

    private UserProductSearchQualificationPlan.ConditionFilter missingCondition() {
        return new UserProductSearchQualificationPlan.ConditionFilter(
                UserProductSearchFilterState.MISSING,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.LocationsFilter missingLocations() {
        return new UserProductSearchQualificationPlan.LocationsFilter(
                UserProductSearchFilterState.MISSING,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.PriceFilter missingPrice() {
        return new UserProductSearchQualificationPlan.PriceFilter(
                UserProductSearchFilterState.MISSING,
                null,
                null,
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.RatingFilter missingRating() {
        return new UserProductSearchQualificationPlan.RatingFilter(
                UserProductSearchFilterState.MISSING,
                null,
                null,
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private UserProductSearchQualificationPlan.PriceTierFilter missingPriceTier() {
        return new UserProductSearchQualificationPlan.PriceTierFilter(
                UserProductSearchFilterState.MISSING,
                List.of(),
                UserProductSearchQualificationPlan.Provenance.none()
        );
    }

    private String question(List<UserProductSearchQuestionTarget> missing, Category category) {
        boolean size = missing.contains(UserProductSearchQuestionTarget.SIZE);
        boolean destination = missing.contains(UserProductSearchQuestionTarget.SHIPS_TO);
        String sizeLabel = switch (category) {
            case FOOTWEAR -> "shoe size";
            case APPAREL -> "clothing size";
            default -> "product size";
        };
        if (size && destination && missing.size() == 2) {
            return "What " + sizeLabel + " do you need, and what country should it ship to? "
                    + "You may also include a region or postal code. "
                    + "You can answer either one, say which one does not matter, or say “I don’t care” "
                    + "if neither should filter the search.";
        }
        if (size && missing.size() == 1) {
            return "What " + sizeLabel + " do you need?";
        }
        if (destination && missing.size() == 1) {
            return "What country should the order ship to? You may also include a region or postal code. "
                    + "You can also say “I don’t care” "
                    + "if location should not filter the search.";
        }
        List<String> labels = new ArrayList<>();
        missing.forEach(target -> labels.add(target.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        return "Please provide " + String.join(", ", labels) + " before I search.";
    }

    private static String normalize(String value) {
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
                .map(UserProductSearchCategoryPolicy::normalize)
                .map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow();
        return Pattern.compile("(?:^|\\s)(?:" + alternatives + ")(?:$|\\s)");
    }

    public enum Category {
        FOOTWEAR,
        FOOD,
        DIGITAL,
        APPAREL,
        TECHNOLOGY,
        PERSONAL_CARE,
        HOME,
        OTHER
    }

    public record ProductSubject(String head, List<String> terms) {

        public ProductSubject {
            terms = List.copyOf(terms);
        }
    }
}
