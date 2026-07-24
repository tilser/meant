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
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
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

    private static final int MAX_ASSISTANT_MESSAGE_LENGTH = 1_500;
    private static final int MAX_EFFECTIVE_QUERY_LENGTH = 500;
    private static final int MAX_EVIDENCE_LENGTH = 300;
    private static final int MAX_SUGGESTED_REPLIES = 5;
    private static final int MAX_SUGGESTED_REPLY_LENGTH = 120;
    private static final int MAX_FILTER_VALUES = 20;
    private static final int MAX_DURABLE_ATTRIBUTES = 5;
    private static final int MAX_DURABLE_SCOPE_LENGTH = 80;
    private static final int MAX_DURABLE_VALUES = 10;
    private static final String SYSTEM_PROMPT = """
            You assess a product search before any catalog request is made. The server, not you, authorizes READY.
            Use the shopping request, latest user turn, prior verified plan, profile facts, and durable preferences.

            For every user-answerable filter, return:
            - relevant: true when the filter materially helps this request; false only when it clearly does not apply.
              A value being absent never makes a filter irrelevant. When uncertain, use relevant=true.
            - explicitAny: true only when the user explicitly says that this exact filter does not matter.
            - provenance: the source and an exact short evidence snippet copied from that source.
            - typed values, when known. Do not invent values.

            Provenance source must be ORIGINAL_QUERY, CURRENT_USER_TURN, PROFILE, DURABLE_PREFERENCE, or NONE.
            Use NONE with empty evidence when no value or explicit indifference is available. PROFILE and durable
            evidence must quote the supplied context. Explicit indifference may only use ORIGINAL_QUERY or
            CURRENT_USER_TURN. A generic yes is valid only when the prior question targeted exactly one filter.
            PROFILE may resolve only SHIPS_TO from saved locations and TARGET_GENDER from clothing fit. A stored
            DURABLE_PREFERENCE may resolve only SIZE and only when its scope clearly matches the current product noun.
            When PROFILE resolves SHIPS_TO, copy country, region, and postalCode exactly from one supplied saved
            location. The city and regionName fields are display evidence only; never put a city name into region
            and never invent a postal code.

            The server always searches sale-ready products, so AVAILABLE is fixed to true and is not your decision.
            SHOPS and CATEGORIES require trusted IDs and have no resolver in this version. Never ask the user for
            IDs and never output these filters. Preserve natural-language shop, brand, and category constraints in
            effectiveQuery instead.

            Decide CONDITION, SHIPS_TO, SHIPS_FROM, PRICE, RATING, and PRICE_TIER independently. PRICE is USD only;
            one valid bound is sufficient. RATING may contain min, minCount, or both. A missing optional bound does
            not make an otherwise valid filter unresolved. CONDITION supports NEW and SECONDHAND. Location countries
            use ISO 3166-1 alpha-2. PRICE_TIER supports LOW, MEDIUM, and HIGH.

            Relevance and criticality are category-specific. A filter is relevant only when it would materially
            improve this particular search, and a missing relevant value should block search only when results would
            otherwise be misleading, unusable, or not meaningfully purchasable. Do not turn ordinary optional
            refinements into questions. Examples:
            - Fit-sensitive footwear such as football boots normally requires SIZE and SHIPS_TO before search. Use a
              matching durable size and saved destination when available; otherwise ask one concise combined question.
            - Apparel can require SIZE or TARGET_GENDER when fit is central, but COLOR, CONDITION, RATING, origin, and
              price are optional unless the request makes them material.
            - Food never uses SIZE or TARGET_GENDER. Preserve dietary, ingredient, format, quantity, and delivery
              constraints in effectiveQuery and context; SHIPS_TO is relevant when delivery feasibility is material.
            - Digital goods do not require a shipping destination.
            - Broad inspiration or category browsing should normally be READY without asking for budget, rating,
              condition, origin, color, or price tier.
            Use relevant=false for supported filters that do not materially apply. Missing is not itself evidence of
            relevance. Never mark a genuinely critical category attribute irrelevant merely to authorize search.

            Return exactly one decision for each supported attribute: COLOR, SIZE, and TARGET_GENDER. Relevance is
            per attribute. For example, blue jeans can have COLOR from the query, TARGET_GENDER from the profile,
            and SIZE unresolved. Use Shopify target-gender labels such as Male, Female, or Unisex.

            If any critical relevant decision lacks both a value and explicit indifference, ask for every such decision
            in one concise natural-language assistantMessage. questionTargets must list every unresolved decision and
            no resolved decision. Suggested replies may be empty when one chip cannot answer the combined question.
            If nothing is unresolved, questionTargets must be empty and assistantMessage may say search is ready.

            durableAttributes is a persistence write-set, separate from hard-filter decisions. Emit a durable SIZE
            only when the user newly supplies or corrects their own stable size for the current product family.
            Do not re-emit stored preferences. Never infer a size, and never persist any attribute except SIZE.
            Use a concise lowercase ASCII scope derived from a product phrase literally present in the original
            request (for example running-shoes for running shoes). Every emitted durable SIZE must equal the
            effective SIZE value.

            effectiveQuery must be a concise catalog query containing the product noun and non-filter keyword
            constraints. Do not include conversational wrapper text.
            """;

    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final UserProductSearchProperties searchProperties;
    private final ObjectMapper objectMapper;
    private final UserProductSearchQualificationPlanResolver planResolver;

    public UserProductSearchQualificationModelResult generate(
            @NotNull @Valid GenerateUserProductSearchQualificationQuery query
    ) {
        String model = openRouterProperties.models().chatModel();
        UserProductSearchQualificationPlanResolver.Resolution firstResolution = null;
        String repairFeedback;
        try {
            UserProductSearchQualificationPlan candidate = completeCandidate(model, query, null);
            firstResolution = planResolver.resolve(candidate, query);
            if (firstResolution.valid()) {
                return result(firstResolution.plan(), model);
            }
            repairFeedback = String.join("; ", firstResolution.violations());
        } catch (RuntimeException exception) {
            repairFeedback = "The assessment was structurally invalid: " + safeErrorMessage(exception);
        }

        try {
            UserProductSearchQualificationPlan repaired = completeCandidate(model, query, repairFeedback);
            UserProductSearchQualificationPlanResolver.Resolution repairedResolution =
                    planResolver.resolve(repaired, query);
            return result(
                    repairedResolution.valid()
                            ? repairedResolution.plan()
                            : planResolver.safeFallback(repairedResolution.plan()),
                    model
            );
        } catch (RuntimeException exception) {
            UserProductSearchQualificationPlan fallback = firstResolution == null
                    ? planResolver.safeFallback(query)
                    : planResolver.safeFallback(firstResolution.plan());
            return result(fallback, model);
        }
    }

    private UserProductSearchQualificationModelResult result(
            UserProductSearchQualificationPlan plan,
            String model
    ) {
        return new UserProductSearchQualificationModelResult(
                plan, model, searchProperties.queryParserPromptVersion());
    }

    private String safeErrorMessage(RuntimeException exception) {
        String message = blankToNull(exception.getMessage());
        if (message == null) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private UserProductSearchQualificationPlan completeCandidate(
            String model,
            GenerateUserProductSearchQualificationQuery query,
            String repairFeedback
    ) {
        String response = openRouterChatClient.completeJson(
                model,
                SYSTEM_PROMPT,
                userPrompt(query, repairFeedback),
                "product_search_qualification",
                responseSchema()
        );
        return sanitize(parse(response));
    }

    private String userPrompt(GenerateUserProductSearchQualificationQuery query, String repairFeedback) {
        try {
            String prompt = """
                    Original shopping request:
                    %s

                    Latest user turn:
                    %s

                    Previous verified qualification plan (null means first turn or legacy plan):
                    %s

                    Profile facts:
                    %s

                    Existing durable scoped product-search preferences:
                    %s
                    """.formatted(
                    query.originalQuery().trim(),
                    query.message().trim(),
                    query.previousPlan() == null || !query.previousPlan().currentSchema()
                            ? "null"
                            : objectMapper.writeValueAsString(query.previousPlan()),
                    objectMapper.writeValueAsString(settingsPrompt(query.settings())),
                    objectMapper.writeValueAsString(query.durablePreferences())
            );
            if (repairFeedback == null || repairFeedback.isBlank()) {
                return prompt;
            }
            return prompt + """

                    Server validation rejected the previous assessment:
                    %s

                    Return one corrected complete assessment. Do not suppress a missing decision by changing its
                    relevance or claiming explicit indifference without evidence. Ensure questionTargets exactly
                    match all unresolved relevant decisions.
                    """.formatted(repairFeedback);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize product-search qualification prompt", exception);
        }
    }

    private SettingsPrompt settingsPrompt(UserSettingsResult settings) {
        return new SettingsPrompt(
                blankToNull(settings.clothingFit()),
                safe(settings.locations()).stream()
                        .map(location -> new LocationPrompt(
                                location.country(),
                                location.code(),
                                location.region(),
                                location.postalCode(),
                                location.regionName(),
                                location.city()))
                        .toList(),
                safe(settings.filters()).stream()
                        .map(filter -> new FilterPrompt(filter.id(), filter.label(), filter.description()))
                        .toList()
        );
    }

    private OpenRouterJsonSchemaDefinition responseSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of(
                        "effectiveQuery", "assistantMessage", "suggestedReplies", "questionTargets", "condition",
                        "shipsTo", "shipsFrom", "price", "attributes", "rating", "priceTier", "durableAttributes"
                ),
                Map.ofEntries(
                        Map.entry("effectiveQuery", OpenRouterJsonSchemaDefinition.string()),
                        Map.entry("assistantMessage", OpenRouterJsonSchemaDefinition.string()),
                        Map.entry("suggestedReplies", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.string())),
                        Map.entry("questionTargets", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.stringEnum(enumNames(
                                        UserProductSearchQuestionTarget.values())))),
                        Map.entry("condition", enumValuesSchema(UserProductCondition.values())),
                        Map.entry("shipsTo", locationFilterSchema()),
                        Map.entry("shipsFrom", locationsFilterSchema()),
                        Map.entry("price", priceSchema()),
                        Map.entry("attributes", attributesSchema()),
                        Map.entry("rating", ratingSchema()),
                        Map.entry("priceTier", enumValuesSchema(UserProductPriceTier.values())),
                        Map.entry("durableAttributes", durableAttributesSchema())
                )
        );
    }

    private OpenRouterJsonSchemaDefinition locationFilterSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("relevant", "explicitAny", "provenance", "country", "region", "postalCode"),
                Map.of(
                        "relevant", OpenRouterJsonSchemaDefinition.bool(),
                        "explicitAny", OpenRouterJsonSchemaDefinition.bool(),
                        "provenance", provenanceSchema(),
                        "country", OpenRouterJsonSchemaDefinition.nullableString(),
                        "region", OpenRouterJsonSchemaDefinition.nullableString(),
                        "postalCode", OpenRouterJsonSchemaDefinition.nullableString()
                )
        );
    }

    private OpenRouterJsonSchemaDefinition locationsFilterSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("relevant", "explicitAny", "provenance", "values"),
                Map.of(
                        "relevant", OpenRouterJsonSchemaDefinition.bool(),
                        "explicitAny", OpenRouterJsonSchemaDefinition.bool(),
                        "provenance", provenanceSchema(),
                        "values", OpenRouterJsonSchemaDefinition.array(locationValueSchema())
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
                List.of("relevant", "explicitAny", "provenance", "minUsd", "maxUsd"),
                Map.of(
                        "relevant", OpenRouterJsonSchemaDefinition.bool(),
                        "explicitAny", OpenRouterJsonSchemaDefinition.bool(),
                        "provenance", provenanceSchema(),
                        "minUsd", OpenRouterJsonSchemaDefinition.nullableNumber(),
                        "maxUsd", OpenRouterJsonSchemaDefinition.nullableNumber()
                )
        );
    }

    private OpenRouterJsonSchemaDefinition attributesSchema() {
        OpenRouterJsonSchemaDefinition attribute = OpenRouterJsonSchemaDefinition.object(
                List.of("name", "relevant", "explicitAny", "provenance", "values"),
                Map.of(
                        "name", OpenRouterJsonSchemaDefinition.stringEnum(enumNames(
                                UserProductSearchAttributeName.values())),
                        "relevant", OpenRouterJsonSchemaDefinition.bool(),
                        "explicitAny", OpenRouterJsonSchemaDefinition.bool(),
                        "provenance", provenanceSchema(),
                        "values", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.string())
                )
        );
        return OpenRouterJsonSchemaDefinition.array(attribute);
    }

    private OpenRouterJsonSchemaDefinition ratingSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("relevant", "explicitAny", "provenance", "min", "minCount"),
                Map.of(
                        "relevant", OpenRouterJsonSchemaDefinition.bool(),
                        "explicitAny", OpenRouterJsonSchemaDefinition.bool(),
                        "provenance", provenanceSchema(),
                        "min", OpenRouterJsonSchemaDefinition.nullableNumber(),
                        "minCount", OpenRouterJsonSchemaDefinition.nullableNumber()
                )
        );
    }

    private OpenRouterJsonSchemaDefinition provenanceSchema() {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("source", "evidence"),
                Map.of(
                        "source", OpenRouterJsonSchemaDefinition.stringEnum(List.of(
                                UserProductSearchDecisionSource.ORIGINAL_QUERY.name(),
                                UserProductSearchDecisionSource.CURRENT_USER_TURN.name(),
                                UserProductSearchDecisionSource.PROFILE.name(),
                                UserProductSearchDecisionSource.DURABLE_PREFERENCE.name(),
                                UserProductSearchDecisionSource.NONE.name()
                        )),
                        "evidence", OpenRouterJsonSchemaDefinition.string()
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
                                OpenRouterJsonSchemaDefinition.string())
                )
        );
        return OpenRouterJsonSchemaDefinition.array(durableAttribute);
    }

    private <T extends Enum<T>> OpenRouterJsonSchemaDefinition enumValuesSchema(T[] values) {
        return OpenRouterJsonSchemaDefinition.object(
                List.of("relevant", "explicitAny", "provenance", "values"),
                Map.of(
                        "relevant", OpenRouterJsonSchemaDefinition.bool(),
                        "explicitAny", OpenRouterJsonSchemaDefinition.bool(),
                        "provenance", provenanceSchema(),
                        "values", OpenRouterJsonSchemaDefinition.array(
                                OpenRouterJsonSchemaDefinition.stringEnum(enumNames(values)))
                )
        );
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
        List<UserProductSearchQuestionTarget> questionTargets = distinctRequired(
                response.questionTargets(), "questionTargets");
        RawEnumValues condition = required(response.condition(), "condition");
        RawLocationFilter shipsTo = required(response.shipsTo(), "shipsTo");
        RawLocationsFilter shipsFrom = required(response.shipsFrom(), "shipsFrom");
        RawPrice price = required(response.price(), "price");
        List<RawAttribute> attributes = required(response.attributes(), "attributes");
        RawRating rating = required(response.rating(), "rating");
        RawEnumValues priceTier = required(response.priceTier(), "priceTier");
        List<RawDurableAttribute> durableAttributes = required(
                response.durableAttributes(), "durableAttributes");

        UserProductSearchQualificationPlan.AttributesFilter effectiveAttributes = attributes(attributes);
        return new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                effectiveQuery,
                assistantMessage,
                suggestedReplies,
                questionTargets,
                new UserProductSearchQualificationPlan.AvailableFilter(
                        UserProductSearchFilterState.VALUE,
                        true,
                        UserProductSearchQualificationPlan.Provenance.system("sale-ready products only")
                ),
                condition(condition),
                shipsTo(shipsTo),
                shipsFrom(shipsFrom),
                price(price),
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
                effectiveAttributes,
                rating(rating),
                priceTier(priceTier),
                durableAttributes(durableAttributes, effectiveAttributes)
        );
    }

    private UserProductSearchQualificationPlan.ConditionFilter condition(RawEnumValues raw) {
        UserProductSearchFilterState state = state(
                raw.relevant(), raw.explicitAny(), !safe(raw.values()).isEmpty(), "condition");
        List<UserProductCondition> values = state == UserProductSearchFilterState.VALUE
                ? enumValues(raw.values(), UserProductCondition.class, "condition")
                : List.of();
        requireValuesForValueState(state, values, "condition");
        return new UserProductSearchQualificationPlan.ConditionFilter(
                state, values, provenance(raw.provenance(), "condition"));
    }

    private UserProductSearchQualificationPlan.LocationFilter shipsTo(RawLocationFilter raw) {
        boolean hasValue = blankToNull(raw.country()) != null;
        UserProductSearchFilterState state = state(
                raw.relevant(), raw.explicitAny(), hasValue, "shipsTo");
        UserProductSearchQualificationPlan.Location value = state == UserProductSearchFilterState.VALUE
                ? location(raw.country(), raw.region(), raw.postalCode(), "shipsTo")
                : null;
        return new UserProductSearchQualificationPlan.LocationFilter(
                state, value, provenance(raw.provenance(), "shipsTo"));
    }

    private UserProductSearchQualificationPlan.LocationsFilter shipsFrom(RawLocationsFilter raw) {
        boolean hasValue = !safe(raw.values()).isEmpty();
        UserProductSearchFilterState state = state(
                raw.relevant(), raw.explicitAny(), hasValue, "shipsFrom");
        List<UserProductSearchQualificationPlan.Location> values = state == UserProductSearchFilterState.VALUE
                ? safe(raw.values()).stream()
                        .map(value -> location(value.country(), value.region(), value.postalCode(), "shipsFrom"))
                        .distinct()
                        .limit(MAX_FILTER_VALUES)
                        .toList()
                : List.of();
        requireValuesForValueState(state, values, "shipsFrom");
        return new UserProductSearchQualificationPlan.LocationsFilter(
                state, values, provenance(raw.provenance(), "shipsFrom"));
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
        boolean hasValue = raw.minUsd() != null || raw.maxUsd() != null;
        UserProductSearchFilterState state = state(
                raw.relevant(), raw.explicitAny(), hasValue, "price");
        Long min = state == UserProductSearchFilterState.VALUE ? usdMinor(raw.minUsd(), "price.minUsd") : null;
        Long max = state == UserProductSearchFilterState.VALUE ? usdMinor(raw.maxUsd(), "price.maxUsd") : null;
        if (state == UserProductSearchFilterState.VALUE && min == null && max == null) {
            throw invalid("price VALUE requires minUsd or maxUsd");
        }
        if (min != null && max != null && min > max) {
            throw invalid("price minimum exceeds maximum");
        }
        return new UserProductSearchQualificationPlan.PriceFilter(
                state, min, max, provenance(raw.provenance(), "price"));
    }

    private UserProductSearchQualificationPlan.AttributesFilter attributes(List<RawAttribute> rawAttributes) {
        EnumMap<UserProductSearchAttributeName, UserProductSearchQualificationPlan.Attribute> byName =
                new EnumMap<>(UserProductSearchAttributeName.class);
        for (RawAttribute raw : rawAttributes) {
            if (raw == null || raw.name() == null) {
                throw invalid("attribute name is required");
            }
            if (byName.containsKey(raw.name())) {
                throw invalid("attribute decision was duplicated: " + raw.name());
            }
            List<String> cleaned = cleanText(raw.values(), MAX_FILTER_VALUES, 120);
            UserProductSearchFilterState state = state(
                    raw.relevant(), raw.explicitAny(), !cleaned.isEmpty(), "attributes." + raw.name());
            List<String> values = state == UserProductSearchFilterState.VALUE ? cleaned : List.of();
            requireValuesForValueState(state, values, "attributes." + raw.name());
            byName.put(raw.name(), new UserProductSearchQualificationPlan.Attribute(
                    raw.name(), state, values, provenance(raw.provenance(), "attributes." + raw.name())));
        }
        for (UserProductSearchAttributeName name : UserProductSearchAttributeName.values()) {
            if (!byName.containsKey(name)) {
                throw invalid("attribute decision is required: " + name);
            }
        }
        List<UserProductSearchQualificationPlan.Attribute> values = List.copyOf(byName.values());
        UserProductSearchFilterState groupState = values.stream()
                .anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.MISSING)
                ? UserProductSearchFilterState.MISSING
                : values.stream().anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.VALUE)
                ? UserProductSearchFilterState.VALUE
                : values.stream().anyMatch(attribute -> attribute.state() == UserProductSearchFilterState.ANY)
                ? UserProductSearchFilterState.ANY
                : UserProductSearchFilterState.NOT_APPLICABLE;
        return new UserProductSearchQualificationPlan.AttributesFilter(groupState, values);
    }

    private UserProductSearchQualificationPlan.RatingFilter rating(RawRating raw) {
        boolean hasValue = raw.min() != null || raw.minCount() != null;
        UserProductSearchFilterState state = state(
                raw.relevant(), raw.explicitAny(), hasValue, "rating");
        if (state != UserProductSearchFilterState.VALUE) {
            return new UserProductSearchQualificationPlan.RatingFilter(
                    state, null, null, provenance(raw.provenance(), "rating"));
        }
        BigDecimal min = nonNegativeOrNull(raw.min());
        Long minCount = nonNegativeOrNull(raw.minCount());
        if (min == null && minCount == null) {
            throw invalid("rating VALUE requires min or minCount");
        }
        if (min != null && min.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw invalid("rating minimum must not exceed 5");
        }
        return new UserProductSearchQualificationPlan.RatingFilter(
                state, min, minCount, provenance(raw.provenance(), "rating"));
    }

    private UserProductSearchQualificationPlan.PriceTierFilter priceTier(RawEnumValues raw) {
        UserProductSearchFilterState state = state(
                raw.relevant(), raw.explicitAny(), !safe(raw.values()).isEmpty(), "priceTier");
        List<UserProductPriceTier> values = state == UserProductSearchFilterState.VALUE
                ? enumValues(raw.values(), UserProductPriceTier.class, "priceTier")
                : List.of();
        requireValuesForValueState(state, values, "priceTier");
        return new UserProductSearchQualificationPlan.PriceTierFilter(
                state, values, provenance(raw.provenance(), "priceTier"));
    }

    private UserProductSearchFilterState state(
            Boolean relevant,
            Boolean explicitAny,
            boolean hasValue,
            String field
    ) {
        boolean requiredRelevant = requiredFlag(relevant, field + ".relevant");
        boolean requiredExplicitAny = requiredFlag(explicitAny, field + ".explicitAny");
        if (!requiredRelevant && (requiredExplicitAny || hasValue)) {
            throw invalid("an irrelevant decision cannot contain values or explicit indifference");
        }
        if (requiredExplicitAny && hasValue) {
            throw invalid("explicit indifference cannot be combined with filter values");
        }
        if (!requiredRelevant) {
            return UserProductSearchFilterState.NOT_APPLICABLE;
        }
        if (requiredExplicitAny) {
            return UserProductSearchFilterState.ANY;
        }
        return hasValue ? UserProductSearchFilterState.VALUE : UserProductSearchFilterState.MISSING;
    }

    private boolean requiredFlag(Boolean value, String field) {
        if (value == null) {
            throw invalid(field + " is required");
        }
        return value;
    }

    private UserProductSearchQualificationPlan.Provenance provenance(RawProvenance raw, String field) {
        RawProvenance required = required(raw, field + ".provenance");
        if (required.source() == null) {
            throw invalid(field + " provenance source is required");
        }
        return new UserProductSearchQualificationPlan.Provenance(
                required.source(), optionalText(required.evidence(), MAX_EVIDENCE_LENGTH));
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
                .filter(attribute -> attribute.state() == UserProductSearchFilterState.VALUE)
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

    private <T> List<T> distinctRequired(List<T> values, String field) {
        if (values == null) {
            throw invalid(field + " is required");
        }
        return values.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private String requiredText(String value, int maxLength, String field) {
        String cleaned = blankToNull(value);
        if (cleaned == null || cleaned.length() > maxLength) {
            throw invalid(field + " was missing or too long");
        }
        return cleaned;
    }

    private String optionalText(String value, int maxLength) {
        String cleaned = blankToNull(value);
        if (cleaned == null) {
            return null;
        }
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength).trim();
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

    private record LocationPrompt(
            String country,
            String code,
            String region,
            String postalCode,
            String regionName,
            String city
    ) {
    }

    private record FilterPrompt(String id, String label, String description) {
    }

    private record ModelResponse(
            String effectiveQuery,
            String assistantMessage,
            List<String> suggestedReplies,
            List<UserProductSearchQuestionTarget> questionTargets,
            RawEnumValues condition,
            RawLocationFilter shipsTo,
            RawLocationsFilter shipsFrom,
            RawPrice price,
            List<RawAttribute> attributes,
            RawRating rating,
            RawEnumValues priceTier,
            List<RawDurableAttribute> durableAttributes
    ) {
    }

    private record RawProvenance(UserProductSearchDecisionSource source, String evidence) {
    }

    private record RawEnumValues(
            Boolean relevant,
            Boolean explicitAny,
            RawProvenance provenance,
            List<String> values
    ) {
    }

    private record RawLocationFilter(
            Boolean relevant,
            Boolean explicitAny,
            RawProvenance provenance,
            String country,
            String region,
            String postalCode
    ) {
    }

    private record RawLocation(String country, String region, String postalCode) {
    }

    private record RawLocationsFilter(
            Boolean relevant,
            Boolean explicitAny,
            RawProvenance provenance,
            List<RawLocation> values
    ) {
    }

    private record RawPrice(
            Boolean relevant,
            Boolean explicitAny,
            RawProvenance provenance,
            BigDecimal minUsd,
            BigDecimal maxUsd
    ) {
    }

    private record RawAttribute(
            UserProductSearchAttributeName name,
            Boolean relevant,
            Boolean explicitAny,
            RawProvenance provenance,
            List<String> values
    ) {
    }

    private record RawDurableAttribute(
            String scope,
            UserProductSearchAttributeName name,
            List<String> values
    ) {
    }

    private record RawRating(
            Boolean relevant,
            Boolean explicitAny,
            RawProvenance provenance,
            BigDecimal min,
            Long minCount
    ) {
    }
}
