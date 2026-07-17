package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.OpenRouterJsonExtractor;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationModelResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
public class UserProductSearchQualificationModelService {

    private static final int MAX_ASSISTANT_MESSAGE_LENGTH = 1_000;
    private static final int MAX_EFFECTIVE_QUERY_LENGTH = 500;
    private static final int MAX_SUGGESTED_REPLIES = 5;
    private static final int MAX_SUGGESTED_REPLY_LENGTH = 120;
    private static final int MAX_FILTER_VALUES = 20;
    private static final int MAX_DURABLE_ATTRIBUTES = 5;
    private static final int MAX_DURABLE_SCOPE_LENGTH = 80;
    private static final int MAX_DURABLE_VALUES = 10;
    private static final String SYSTEM_PROMPT = """
            You qualify a product search before any catalog request is made.
            Use the shopping request, the latest user turn, prior qualification plan, and durable user context.
            Return one complete decision for every supported filter. Never omit a filter decision.

            Each filter state means:
            - VALUE: a concrete enforceable value is known from the current conversation or durable context.
            - ANY: the user explicitly has no preference, so omit that filter.
            - MISSING: the filter materially affects relevance for this request and the user must be asked.
            - NOT_APPLICABLE: the filter does not materially apply to this request.

            Choose relevance dynamically from the request. There are no footwear-, apparel-, or other
            category-specific workflows. For example, Size may matter for shoes and shirts but not for a vase.
            Ask a concise natural follow-up only for materially relevant MISSING filters. Suggested replies must
            be short answers to that follow-up. If there are no MISSING filters, state that the search is ready.

            AVAILABLE should normally be VALUE true. CONDITION supports NEW and SECONDHAND. SHIPS_TO and
            SHIPS_FROM use ISO 3166-1 alpha-2 country codes. PRICE is USD only and may only come from the current
            request or a current qualification answer; never infer a budget. ATTRIBUTES supports only COLOR, SIZE,
            and TARGET_GENDER; use Shopify taxonomy labels for target gender, such as Male, Female, or Unisex.
            RATING uses a 0-5 minimum and a non-negative review count. PRICE_TIER supports LOW, MEDIUM, and HIGH.

            SHOPS and CATEGORIES require trusted server-resolved Shopify GIDs. No resolver is available in this
            qualification version. Return NOT_APPLICABLE for SHOPS and CATEGORIES; never return VALUE or MISSING
            for them and never invent an ID. Preserve a requested brand, shop, or product category as ordinary
            words in effectiveQuery instead.

            durableAttributes is a persistence write-set, separate from the hard-filter decisions. Emit a durable
            SIZE only when the user newly supplies or corrects their size for the current product family in this
            conversation. Treat that answer as stable without a separate save-confirmation question unless they
            frame it as one-off, a gift, or somebody else's size. Do not re-emit an existing durable preference
            merely because it was reused; it is already stored. Never infer or guess a durable fact merely because
            a size filter is useful. Only SIZE may be durable; never persist color, gender, budget, condition,
            rating, or another preference. Choose a concise lowercase ASCII scope broad enough for products
            sharing that sizing convention (for example, footwear or t-shirts), rather than a narrow search
            phrase. Every emitted durable SIZE value must also be present in this plan's ATTRIBUTES SIZE hard
            filter. Return an empty array when there is no new or corrected stable size fact.

            effectiveQuery must remain a concise catalog query containing the product noun and non-filter keyword
            constraints. Include relevant durable preferences that cannot be represented by the supported hard
            filters, and exclude durable context that is irrelevant to this request. Do not put conversational
            wrapper text into effectiveQuery.
            """;

    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final UserProductSearchProperties searchProperties;
    private final ObjectMapper objectMapper;

    public UserProductSearchQualificationModelResult generate(
            @NotNull @Valid GenerateUserProductSearchQualificationQuery query
    ) {
        String model = openRouterProperties.models().productSearchQueryParser();
        String response = openRouterChatClient.completeJson(
                model,
                SYSTEM_PROMPT,
                userPrompt(query),
                "product_search_qualification",
                responseSchema()
        );
        return new UserProductSearchQualificationModelResult(
                sanitize(parse(response)),
                model,
                searchProperties.queryParserPromptVersion()
        );
    }

    private String userPrompt(GenerateUserProductSearchQualificationQuery query) {
        try {
            return """
                    Original shopping request:
                    %s

                    Latest user turn:
                    %s

                    Previous qualification plan (null means first turn):
                    %s

                    Durable user context (only explicit fit, locations, and shopping preferences; unconfirmed
                    defaults are excluded):
                    %s

                    Existing durable scoped product-search preferences (reuse only when the product scope applies):
                    %s
                    """.formatted(
                    query.originalQuery().trim(),
                    query.message().trim(),
                    query.previousPlan() == null ? "null" : objectMapper.writeValueAsString(query.previousPlan()),
                    objectMapper.writeValueAsString(settingsPrompt(query.settings())),
                    objectMapper.writeValueAsString(query.durablePreferences())
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize product-search qualification prompt", exception);
        }
    }

    private SettingsPrompt settingsPrompt(UserSettingsResult settings) {
        return new SettingsPrompt(
                blankToNull(settings.clothingFit()),
                safe(settings.locations()).stream()
                        .map(location -> new LocationPrompt(location.country(), location.code(), location.city()))
                        .toList(),
                safe(settings.filters()).stream()
                        .map(filter -> new FilterPrompt(filter.id(), filter.label(), filter.description()))
                        .toList()
        );
    }

    private OpenRouterJsonSchemaDefinition responseSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of(
                        "effectiveQuery", "assistantMessage", "suggestedReplies", "available", "condition",
                        "shipsTo", "shipsFrom", "price", "shops", "categories", "attributes", "rating",
                        "priceTier", "durableAttributes"
                ),
                Map.ofEntries(
                        Map.entry("effectiveQuery", OpenRouterJsonSchemaDefinition.string()),
                        Map.entry("assistantMessage", OpenRouterJsonSchemaDefinition.string()),
                        Map.entry("suggestedReplies", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.string(), 0, MAX_SUGGESTED_REPLIES)),
                        Map.entry("available", availableSchema()),
                        Map.entry("condition", enumValuesSchema(UserProductCondition.values())),
                        Map.entry("shipsTo", locationFilterSchema()),
                        Map.entry("shipsFrom", locationsFilterSchema()),
                        Map.entry("price", priceSchema()),
                        Map.entry("shops", referenceSchema()),
                        Map.entry("categories", referenceSchema()),
                        Map.entry("attributes", attributesSchema()),
                        Map.entry("rating", ratingSchema()),
                        Map.entry("priceTier", enumValuesSchema(UserProductPriceTier.values())),
                        Map.entry("durableAttributes", durableAttributesSchema())
                )
        );
    }

    private OpenRouterJsonSchemaDefinition availableSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("state", "value"),
                Map.of(
                        "state", stateSchema(),
                        "value", OpenRouterJsonSchemaDefinition.nullableBool()
                )
        );
    }

    private OpenRouterJsonSchemaDefinition locationFilterSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("state", "country", "region", "postalCode"),
                Map.of(
                        "state", stateSchema(),
                        "country", OpenRouterJsonSchemaDefinition.nullableString(),
                        "region", OpenRouterJsonSchemaDefinition.nullableString(),
                        "postalCode", OpenRouterJsonSchemaDefinition.nullableString()
                )
        );
    }

    private OpenRouterJsonSchemaDefinition locationsFilterSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("state", "values"),
                Map.of(
                        "state", stateSchema(),
                        "values", OpenRouterJsonSchemaDefinition.array(locationValueSchema(), 0, MAX_FILTER_VALUES)
                )
        );
    }

    private OpenRouterJsonSchemaDefinition locationValueSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("country", "region", "postalCode"),
                Map.of(
                        "country", OpenRouterJsonSchemaDefinition.nullableString(),
                        "region", OpenRouterJsonSchemaDefinition.nullableString(),
                        "postalCode", OpenRouterJsonSchemaDefinition.nullableString()
                )
        );
    }

    private OpenRouterJsonSchemaDefinition priceSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("state", "minUsd", "maxUsd"),
                Map.of(
                        "state", stateSchema(),
                        "minUsd", OpenRouterJsonSchemaDefinition.nullableNumber(),
                        "maxUsd", OpenRouterJsonSchemaDefinition.nullableNumber()
                )
        );
    }

    private OpenRouterJsonSchemaDefinition referenceSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("state", "values"),
                Map.of(
                        "state", stateSchema(),
                        "values", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.string(), 0, MAX_FILTER_VALUES)
                )
        );
    }

    private OpenRouterJsonSchemaDefinition attributesSchema() {
        OpenRouterJsonSchemaDefinition attribute = OpenRouterJsonSchemaDefinition.object(
                List.of("name", "values"),
                Map.of(
                        "name", OpenRouterJsonSchemaDefinition.stringEnum(enumNames(
                                UserProductSearchAttributeName.values())),
                        "values", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.string(), 1, MAX_FILTER_VALUES)
                )
        );
        return OpenRouterJsonSchemaDefinition.object(
                List.of("state", "values"),
                Map.of(
                        "state", stateSchema(),
                        "values", OpenRouterJsonSchemaDefinition.array(attribute, 0, 3)
                )
        );
    }

    private OpenRouterJsonSchemaDefinition ratingSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("state", "min", "minCount"),
                Map.of(
                        "state", stateSchema(),
                        "min", OpenRouterJsonSchemaDefinition.nullableNumber(),
                        "minCount", OpenRouterJsonSchemaDefinition.nullableNumber()
                )
        );
    }

    private OpenRouterJsonSchemaDefinition durableAttributesSchema() {
        OpenRouterJsonSchemaDefinition durableAttribute = OpenRouterJsonSchemaDefinition.object(
                List.of("scope", "name", "values"),
                Map.of(
                        "scope", OpenRouterJsonSchemaDefinition.string(),
                        "name", OpenRouterJsonSchemaDefinition.stringEnum(List.of(
                                UserProductSearchAttributeName.SIZE.name())),
                        "values", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.string(), 1, MAX_DURABLE_VALUES)
                )
        );
        return OpenRouterJsonSchemaDefinition.array(durableAttribute, 0, MAX_DURABLE_ATTRIBUTES);
    }

    private <T extends Enum<T>> OpenRouterJsonSchemaDefinition enumValuesSchema(T[] values) {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("state", "values"),
                Map.of(
                        "state", stateSchema(),
                        "values", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.stringEnum(enumNames(values)), 0, MAX_FILTER_VALUES)
                )
        );
    }

    private OpenRouterJsonSchemaDefinition stateSchema() {
        return OpenRouterJsonSchemaDefinition.stringEnum(enumNames(UserProductSearchFilterState.values()));
    }

    private <T extends Enum<T>> List<String> enumNames(T[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }

    private ModelResponse parse(String response) {
        try {
            return objectMapper.readValue(OpenRouterJsonExtractor.objectCandidate(response), ModelResponse.class);
        } catch (JacksonException exception) {
            throw new OpenRouterException("OpenRouter returned invalid product-search qualification JSON", exception);
        }
    }

    private UserProductSearchQualificationPlan sanitize(ModelResponse response) {
        if (response == null) {
            throw invalid("response was empty");
        }
        String effectiveQuery = requiredText(response.effectiveQuery(), MAX_EFFECTIVE_QUERY_LENGTH, "effectiveQuery");
        String assistantMessage = requiredText(
                response.assistantMessage(), MAX_ASSISTANT_MESSAGE_LENGTH, "assistantMessage");
        List<String> suggestedReplies = cleanText(response.suggestedReplies(), MAX_SUGGESTED_REPLIES,
                MAX_SUGGESTED_REPLY_LENGTH);
        RawAvailable available = required(response.available(), "available");
        RawEnumValues condition = required(response.condition(), "condition");
        RawLocationFilter shipsTo = required(response.shipsTo(), "shipsTo");
        RawLocationsFilter shipsFrom = required(response.shipsFrom(), "shipsFrom");
        RawPrice price = required(response.price(), "price");
        RawReference shops = required(response.shops(), "shops");
        RawReference categories = required(response.categories(), "categories");
        RawAttributes attributes = required(response.attributes(), "attributes");
        RawRating rating = required(response.rating(), "rating");
        RawEnumValues priceTier = required(response.priceTier(), "priceTier");
        List<RawDurableAttribute> durableAttributes = required(
                response.durableAttributes(), "durableAttributes");

        rejectUnresolvedReference("SHOPS", shops);
        rejectUnresolvedReference("CATEGORIES", categories);

        UserProductSearchFilterState availableState = state(available.state(), "available");
        if (availableState == UserProductSearchFilterState.VALUE && available.value() == null) {
            throw invalid("available VALUE requires a boolean value");
        }
        UserProductSearchQualificationPlan.AttributesFilter effectiveAttributes = attributes(attributes);

        return new UserProductSearchQualificationPlan(
                effectiveQuery,
                assistantMessage,
                suggestedReplies,
                new UserProductSearchQualificationPlan.AvailableFilter(
                        availableState,
                        availableState == UserProductSearchFilterState.VALUE
                                ? available.value()
                                : null
                ),
                condition(condition),
                shipsTo(shipsTo),
                shipsFrom(shipsFrom),
                price(price),
                unresolvedReference("shops", shops),
                unresolvedReference("categories", categories),
                effectiveAttributes,
                rating(rating),
                priceTier(priceTier),
                durableAttributes(durableAttributes, effectiveAttributes)
        );
    }

    private UserProductSearchQualificationPlan.ConditionFilter condition(RawEnumValues raw) {
        UserProductSearchFilterState state = state(raw.state(), "condition");
        List<UserProductCondition> values = state == UserProductSearchFilterState.VALUE
                ? enumValues(raw.values(), UserProductCondition.class, "condition")
                : List.of();
        requireValuesForValueState(state, values, "condition");
        return new UserProductSearchQualificationPlan.ConditionFilter(state, values);
    }

    private UserProductSearchQualificationPlan.LocationFilter shipsTo(RawLocationFilter raw) {
        UserProductSearchFilterState state = state(raw.state(), "shipsTo");
        UserProductSearchQualificationPlan.Location value = state == UserProductSearchFilterState.VALUE
                ? location(raw.country(), raw.region(), raw.postalCode(), "shipsTo")
                : null;
        return new UserProductSearchQualificationPlan.LocationFilter(state, value);
    }

    private UserProductSearchQualificationPlan.LocationsFilter shipsFrom(RawLocationsFilter raw) {
        UserProductSearchFilterState state = state(raw.state(), "shipsFrom");
        List<UserProductSearchQualificationPlan.Location> values = state == UserProductSearchFilterState.VALUE
                ? safe(raw.values()).stream()
                        .map(value -> location(value.country(), value.region(), value.postalCode(), "shipsFrom"))
                        .distinct()
                        .limit(MAX_FILTER_VALUES)
                        .toList()
                : List.of();
        requireValuesForValueState(state, values, "shipsFrom");
        return new UserProductSearchQualificationPlan.LocationsFilter(state, values);
    }

    private UserProductSearchQualificationPlan.Location location(
            String country,
            String region,
            String postalCode,
            String field
    ) {
        String normalizedCountry = CountryCodeNormalizer.normalizeAlpha2(country);
        if (normalizedCountry == null) {
            throw invalid(field + " country must be ISO 3166-1 alpha-2");
        }
        return new UserProductSearchQualificationPlan.Location(
                normalizedCountry,
                blankToNull(region),
                blankToNull(postalCode)
        );
    }

    private UserProductSearchQualificationPlan.PriceFilter price(RawPrice raw) {
        UserProductSearchFilterState state = state(raw.state(), "price");
        Long min = state == UserProductSearchFilterState.VALUE ? usdMinor(raw.minUsd(), "price.minUsd") : null;
        Long max = state == UserProductSearchFilterState.VALUE ? usdMinor(raw.maxUsd(), "price.maxUsd") : null;
        if (state == UserProductSearchFilterState.VALUE && min == null && max == null) {
            throw invalid("price VALUE requires minUsd or maxUsd");
        }
        if (min != null && max != null && min > max) {
            throw invalid("price minimum exceeds maximum");
        }
        return new UserProductSearchQualificationPlan.PriceFilter(state, min, max);
    }

    private UserProductSearchQualificationPlan.AttributesFilter attributes(RawAttributes raw) {
        UserProductSearchFilterState state = state(raw.state(), "attributes");
        if (state != UserProductSearchFilterState.VALUE && state != UserProductSearchFilterState.MISSING) {
            return new UserProductSearchQualificationPlan.AttributesFilter(state, List.of());
        }
        EnumMap<UserProductSearchAttributeName, LinkedHashSet<String>> values = new EnumMap<>(
                UserProductSearchAttributeName.class);
        for (RawAttribute attribute : safe(raw.values())) {
            if (attribute == null || attribute.name() == null) {
                throw invalid("attribute name is required");
            }
            List<String> cleaned = cleanText(attribute.values(), MAX_FILTER_VALUES, 120);
            if (cleaned.isEmpty()) {
                throw invalid("attribute VALUE requires at least one value");
            }
            values.computeIfAbsent(attribute.name(), ignored -> new LinkedHashSet<>()).addAll(cleaned);
        }
        List<UserProductSearchQualificationPlan.Attribute> attributes = values.entrySet().stream()
                .map(entry -> new UserProductSearchQualificationPlan.Attribute(
                        entry.getKey(), entry.getValue().stream().limit(MAX_FILTER_VALUES).toList()))
                .toList();
        requireValuesForValueState(state, attributes, "attributes");
        return new UserProductSearchQualificationPlan.AttributesFilter(state, attributes);
    }

    private UserProductSearchQualificationPlan.RatingFilter rating(RawRating raw) {
        UserProductSearchFilterState state = state(raw.state(), "rating");
        if (state != UserProductSearchFilterState.VALUE) {
            return new UserProductSearchQualificationPlan.RatingFilter(state, null, null);
        }
        BigDecimal min = nonNegativeOrNull(raw.min());
        Long minCount = nonNegativeOrNull(raw.minCount());
        if (min == null && minCount == null) {
            throw invalid("rating VALUE requires min or minCount");
        }
        if (min != null && min.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw invalid("rating minimum must not exceed 5");
        }
        return new UserProductSearchQualificationPlan.RatingFilter(state, min, minCount);
    }

    private List<UserProductSearchQualificationPlan.DurableAttribute> durableAttributes(
            List<RawDurableAttribute> rawAttributes,
            UserProductSearchQualificationPlan.AttributesFilter effectiveAttributes
    ) {
        if (rawAttributes.size() > MAX_DURABLE_ATTRIBUTES) {
            throw invalid("durableAttributes exceeded the supported limit");
        }
        Map<String, String> effectiveSizes = new LinkedHashMap<>();
        effectiveAttributes.values().stream()
                .filter(attribute -> attribute.name() == UserProductSearchAttributeName.SIZE)
                .flatMap(attribute -> attribute.values().stream())
                .forEach(value -> effectiveSizes.putIfAbsent(value.toLowerCase(Locale.ROOT), value));

        Map<String, LinkedHashSet<String>> valuesByScope = new LinkedHashMap<>();
        for (RawDurableAttribute rawAttribute : rawAttributes) {
            if (rawAttribute == null) {
                throw invalid("durable attribute is required");
            }
            if (rawAttribute.name() != UserProductSearchAttributeName.SIZE) {
                throw invalid("only SIZE may be a durable product-search attribute");
            }
            String scope = normalizedScope(rawAttribute.scope());
            List<String> values = cleanText(rawAttribute.values(), MAX_DURABLE_VALUES, 120);
            if (values.isEmpty()) {
                throw invalid("durable SIZE requires at least one value");
            }
            LinkedHashSet<String> canonicalValues = valuesByScope.computeIfAbsent(
                    scope, ignored -> new LinkedHashSet<>());
            for (String value : values) {
                String canonical = effectiveSizes.get(value.toLowerCase(Locale.ROOT));
                if (canonical == null) {
                    throw invalid("durable SIZE must match the effective SIZE attribute filter");
                }
                canonicalValues.add(canonical);
            }
        }
        return valuesByScope.entrySet().stream()
                .map(entry -> new UserProductSearchQualificationPlan.DurableAttribute(
                        entry.getKey(),
                        UserProductSearchAttributeName.SIZE,
                        entry.getValue().stream().limit(MAX_DURABLE_VALUES).toList()
                ))
                .toList();
    }

    private UserProductSearchQualificationPlan.PriceTierFilter priceTier(RawEnumValues raw) {
        UserProductSearchFilterState state = state(raw.state(), "priceTier");
        List<UserProductPriceTier> values = state == UserProductSearchFilterState.VALUE
                ? enumValues(raw.values(), UserProductPriceTier.class, "priceTier")
                : List.of();
        requireValuesForValueState(state, values, "priceTier");
        return new UserProductSearchQualificationPlan.PriceTierFilter(state, values);
    }

    private void rejectUnresolvedReference(String field, RawReference raw) {
        UserProductSearchFilterState state = state(raw.state(), field.toLowerCase());
        if (state == UserProductSearchFilterState.VALUE) {
            throw invalid(field + " VALUE requires a trusted server resolver");
        }
    }

    private UserProductSearchQualificationPlan.ReferenceFilter unresolvedReference(
            String field,
            RawReference raw
    ) {
        UserProductSearchFilterState resolvedState = state(raw.state(), field);
        if (resolvedState != UserProductSearchFilterState.ANY) {
            resolvedState = UserProductSearchFilterState.NOT_APPLICABLE;
        }
        return new UserProductSearchQualificationPlan.ReferenceFilter(resolvedState, List.of());
    }

    private UserProductSearchFilterState state(UserProductSearchFilterState value, String field) {
        if (value == null) {
            throw invalid(field + " state is required");
        }
        return value;
    }

    private Long usdMinor(BigDecimal value, String field) {
        BigDecimal normalized = nonNegativeOrNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return normalized.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException exception) {
            throw invalid(field + " is outside the supported USD range");
        }
    }

    private BigDecimal nonNegativeOrNull(BigDecimal value) {
        return value == null || value.signum() < 0 ? null : value.stripTrailingZeros();
    }

    private Long nonNegativeOrNull(Long value) {
        return value == null || value < 0 ? null : value;
    }

    private <T extends Enum<T>> List<T> enumValues(List<String> values, Class<T> type, String field) {
        LinkedHashSet<T> parsed = new LinkedHashSet<>();
        for (String value : safe(values)) {
            try {
                parsed.add(Enum.valueOf(type, value));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw invalid(field + " contained an unsupported value");
            }
        }
        return List.copyOf(parsed);
    }

    private <T> void requireValuesForValueState(UserProductSearchFilterState state, List<T> values, String field) {
        if (state == UserProductSearchFilterState.VALUE && values.isEmpty()) {
            throw invalid(field + " VALUE requires at least one value");
        }
    }

    private List<String> cleanText(List<String> values, int limit, int maxLength) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        safe(values).stream()
                .map(this::blankToNull)
                .filter(java.util.Objects::nonNull)
                .map(value -> value.length() <= maxLength ? value : value.substring(0, maxLength).trim())
                .filter(value -> !value.isBlank())
                .limit(limit)
                .forEach(cleaned::add);
        return List.copyOf(cleaned);
    }

    private String requiredText(String value, int maxLength, String field) {
        String cleaned = blankToNull(value);
        if (cleaned == null || cleaned.length() > maxLength) {
            throw invalid(field + " was missing or too long");
        }
        return cleaned;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
    }

    private String normalizedScope(String value) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            throw invalid("durable attribute scope is required");
        }
        String normalized = cleaned.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            throw invalid("durable attribute scope is invalid");
        }
        if (normalized.length() > MAX_DURABLE_SCOPE_LENGTH) {
            normalized = normalized.substring(0, MAX_DURABLE_SCOPE_LENGTH).replaceAll("-+$", "");
        }
        return normalized;
    }

    private <T> T required(T value, String field) {
        if (value == null) {
            throw invalid(field + " decision is required");
        }
        return value;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private OpenRouterException invalid(String reason) {
        return new OpenRouterException("OpenRouter returned an invalid product-search qualification: " + reason);
    }

    private record SettingsPrompt(
            String clothingFit,
            List<LocationPrompt> locations,
            List<FilterPrompt> shoppingPreferences
    ) {
    }

    private record LocationPrompt(String country, String code, String city) {
    }

    private record FilterPrompt(String id, String label, String description) {
    }

    private record ModelResponse(
            String effectiveQuery,
            String assistantMessage,
            List<String> suggestedReplies,
            RawAvailable available,
            RawEnumValues condition,
            RawLocationFilter shipsTo,
            RawLocationsFilter shipsFrom,
            RawPrice price,
            RawReference shops,
            RawReference categories,
            RawAttributes attributes,
            RawRating rating,
            RawEnumValues priceTier,
            List<RawDurableAttribute> durableAttributes
    ) {
    }

    private record RawAvailable(UserProductSearchFilterState state, Boolean value) {
    }

    private record RawEnumValues(UserProductSearchFilterState state, List<String> values) {
    }

    private record RawLocationFilter(
            UserProductSearchFilterState state,
            String country,
            String region,
            String postalCode
    ) {
    }

    private record RawLocation(String country, String region, String postalCode) {
    }

    private record RawLocationsFilter(UserProductSearchFilterState state, List<RawLocation> values) {
    }

    private record RawPrice(UserProductSearchFilterState state, BigDecimal minUsd, BigDecimal maxUsd) {
    }

    private record RawReference(UserProductSearchFilterState state, List<String> values) {
    }

    private record RawAttributes(UserProductSearchFilterState state, List<RawAttribute> values) {
    }

    private record RawAttribute(UserProductSearchAttributeName name, List<String> values) {
    }

    private record RawDurableAttribute(
            String scope,
            UserProductSearchAttributeName name,
            List<String> values
    ) {
    }

    private record RawRating(UserProductSearchFilterState state, BigDecimal min, Long minCount) {
    }
}
