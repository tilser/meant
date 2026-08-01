package com.meant.api.module.user.service;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.OpenRouterJsonExtractor;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.common.util.CountryCodeNormalizer;
import com.meant.api.module.catalog.service.port.CatalogSearchParameterContractProvider;
import com.meant.api.module.user.constant.UserCurrency;
import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationModelResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
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
            You assess a product search before any catalog request is made. Your complete assessment determines
            whether the search is READY or needs another user answer. Use the shopping request, ordered trusted
            conversation, latest user turn, prior persisted plan, profile facts, durable preferences, and active
            taste signals.
            A trusted server-resolved similarity-anchor label may be supplied separately. When present, it governs
            product category and category-specific filter relevance even if buyer wording contains a different
            descriptive product phrase. It is not buyer-authored evidence: never use it as hard-filter provenance,
            infer a filter value from it, or copy its terms into effectiveQuery unless those terms also occur in
            trusted buyer, profile, durable-preference, or active-taste evidence.

            For every user-answerable filter, return:
            - relevant: true when the filter materially helps this request; false only when it clearly does not apply.
              A value being absent never makes a filter irrelevant. When uncertain, use relevant=true.
            - explicitAny: true only when the user explicitly says that this exact filter does not matter.
            - provenance: the source and an exact short evidence snippet copied from that source.
            - typed values, when known. Do not invent values.

            Provenance source must be ORIGINAL_QUERY, CURRENT_USER_TURN, CONVERSATION, PROFILE,
            DURABLE_PREFERENCE, or NONE. CONVERSATION may quote exact evidence from a prior USER transcript message
            only; never use an ASSISTANT message as factual evidence. The latest user message is
            CURRENT_USER_TURN, not CONVERSATION. Use NONE with empty evidence when no value or explicit indifference
            is available. PROFILE and durable evidence must quote the supplied context. Explicit indifference may
            use ORIGINAL_QUERY, CURRENT_USER_TURN, or prior USER CONVERSATION evidence. A generic yes is valid only
            when the prior question targeted exactly one filter.
            Prior conversation is context, not automatic carry-over: never apply a constraint from an unrelated
            earlier shopping request unless the current request explicitly refers back to it or the buyer clearly
            stated it as a stable personal fact. Prefer PROFILE or a matching DURABLE_PREFERENCE for stored facts.
            When the previous persisted question asked for multiple targets and explicitly offered a bare “I don’t
            care” answer for all of them, that bare answer resolves every target in that question to ANY. If the user
            names only one target, apply ANY only to that named target.
            PROFILE may resolve only SHIPS_TO from primaryLocation and TARGET_GENDER from clothing fit; its
            priceCurrency supplies denomination context but not hard-filter provenance or a price value. Other saved
            locations are reference context only and must never be used as PROFILE provenance. A stored
            DURABLE_PREFERENCE may resolve only SIZE and only when its scope clearly matches the current product noun.
            When PROFILE resolves SHIPS_TO, copy country, region, and postalCode exactly from primaryLocation.
            The city and regionName fields are display evidence only; never put a city name into region
            and never invent a postal code.

            Active taste signals are soft personalization context only. They may improve effectiveQuery/intent, but
            must never provide provenance for a hard-filter value, explicit indifference, or durable attribute.
            The profile budget is non-authoritative/default context because this version has no budget provenance;
            never resolve PRICE from it.

            The server always searches sale-ready products, so AVAILABLE is fixed to true and is not your decision.
            SHOPS and CATEGORIES require trusted IDs and have no resolver in this version. Never ask the user for
            IDs and never output these filters. Preserve natural-language shop, brand, and category constraints in
            effectiveQuery instead.

            Decide CONDITION, SHIPS_TO, SHIPS_FROM, PRICE, RATING, and PRICE_TIER independently. PRICE uses only the
            profile priceCurrency (USD when absent), and one valid bound is sufficient. Treat an unqualified numeric
            price bound as denominated in that profile currency. An explicit different currency is rejected before
            this assessment. The profile supplies only the denomination, never a minimum, maximum, or budget value.
            A later answer may qualify a numeric bound only from this same pending search, never from an older search
            in the conversation. RATING may contain min, minCount, or both. A missing optional bound does not make an
            otherwise valid filter unresolved. CONDITION supports NEW and SECONDHAND. Location countries use ISO
            3166-1 alpha-2. PRICE_TIER supports LOW, MEDIUM, and HIGH. The legacy price fields minUsd and maxUsd always
            contain major units in the profile priceCurrency, despite their names.

            Relevance and criticality are category-specific. A filter is relevant only when it would materially
            improve this particular search, and a missing relevant value should block search only when results would
            otherwise be misleading, unusable, or not meaningfully purchasable. Do not turn ordinary optional
            refinements into questions. Examples:
            - Every physical product search requires a SHIPS_TO decision before search. Use a saved destination when
              available. Otherwise ask where the order should ship and explicitly allow the user to say location does
              not matter. Only explicit user indifference may produce ANY; ANY intentionally omits the provider
              shipping filter. An explicit country statement such as “I live in the United States”, “I am in…”,
              “I’m based in…”, or “I’m located in…” may supply SHIPS_TO. Never infer a country from a city name.
            - Footwear and sized apparel require SIZE before search. Use a matching durable size when available;
              otherwise ask one concise combined question for size and destination.
            - TARGET_GENDER can matter for apparel when fit is central, but COLOR, CONDITION, RATING, origin, and
              price are optional unless the request makes them material.
            - Food never uses SIZE or TARGET_GENDER. Preserve dietary, ingredient, format, quantity, and delivery
              constraints in effectiveQuery and context.
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
            constraints. Every content term must be copied from the supplied trusted buyer, profile, durable-preference,
            or active-taste evidence; do not add synonyms or inferred product terms. Do not include conversational
            wrapper text. When a filter is explicitAny, omit profile and durable-preference values for that filter from
            effectiveQuery. Earlier original-query, conversation, profile, durable-preference, and previous-plan
            wording is overridden chronologically. Keep an overridden value as semantic query wording only when the
            latest user turn explicitly instructs you to keep that exact value in the product query.
            """;

    private final OpenRouterChatClient openRouterChatClient;
    private final OpenRouterProperties openRouterProperties;
    private final UserProductSearchProperties searchProperties;
    private final ObjectMapper objectMapper;
    private final CatalogSearchParameterContractProvider catalogSearchParameterContractProvider;

    public UserProductSearchQualificationModelResult generate(
            @NotNull @Valid GenerateUserProductSearchQualificationQuery query
    ) {
        String model = openRouterProperties.models().chatModel();
        try {
            return result(completeCandidate(model, query), model);
        } catch (RuntimeException exception) {
            log.warn(
                    "Product-search qualification model failed; continuing without model-derived filters. "
                            + "model={}, failureType={}",
                    model,
                    exception.getClass().getName()
            );
            return result(unqualifiedPlan(query), model);
        }
    }

    private UserProductSearchQualificationPlan unqualifiedPlan(
            GenerateUserProductSearchQualificationQuery query
    ) {
        String effectiveQuery = query.previousPlan() == null
                ? query.originalQuery().trim()
                : query.previousPlan().effectiveQuery();
        if (effectiveQuery.length() > MAX_EFFECTIVE_QUERY_LENGTH) {
            effectiveQuery = effectiveQuery.substring(0, MAX_EFFECTIVE_QUERY_LENGTH).trim();
        }
        UserProductSearchQualificationPlan.Provenance none =
                UserProductSearchQualificationPlan.Provenance.none();
        return new UserProductSearchQualificationPlan(
                UserProductSearchQualificationPlan.CURRENT_SCHEMA_VERSION,
                effectiveQuery,
                "I’ll search with the information available.",
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
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(), none),
                new UserProductSearchQualificationPlan.ReferenceFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(), none),
                new UserProductSearchQualificationPlan.AttributesFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of()),
                new UserProductSearchQualificationPlan.RatingFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, null, null, none),
                new UserProductSearchQualificationPlan.PriceTierFilter(
                        UserProductSearchFilterState.NOT_APPLICABLE, List.of(), none),
                List.of()
        );
    }

    private UserProductSearchQualificationModelResult result(
            UserProductSearchQualificationPlan plan,
            String model
    ) {
        return new UserProductSearchQualificationModelResult(
                plan, model, searchProperties.queryParserPromptVersion());
    }

    private UserProductSearchQualificationPlan completeCandidate(
            String model,
            GenerateUserProductSearchQualificationQuery query
    ) {
        String response = openRouterChatClient.completeJson(
                model,
                SYSTEM_PROMPT,
                userPrompt(query),
                "product_search_qualification",
                responseSchema(),
                searchProperties.qualificationMaximumOutputTokens()
        );
        return sanitize(parse(response), UserCurrency.normalizeOrDefault(query.settings().currency()));
    }

    private String userPrompt(GenerateUserProductSearchQualificationQuery query) {
        try {
            String prompt = """
                    Supported Shopify/UCP search-parameter contract:
                    %s

                    Trusted conversation transcript, ordered oldest to newest:
                    %s

                    Original shopping request:
                    %s

                    Latest user turn:
                    %s

                    Trusted server-resolved similarity anchor label (category/relevance context only; never
                    hard-filter provenance or independent effective-query evidence):
                    %s

                    Previous persisted qualification plan (null means first turn or legacy plan):
                    %s

                    Profile facts:
                    %s

                    Existing durable scoped product-search preferences:
                    %s

                    Active taste signals (soft query/intent context only; never hard-filter provenance):
                    %s
                    """.formatted(
                    catalogSearchParameterContractProvider.currentSearchParameterContract(),
                    objectMapper.writeValueAsString(query.conversation()),
                    query.originalQuery().trim(),
                    query.message().trim(),
                    objectMapper.writeValueAsString(query.trustedReferenceProductText()),
                    query.previousPlan() == null || !query.previousPlan().currentSchema()
                            ? "null"
                            : objectMapper.writeValueAsString(query.previousPlan()),
                    objectMapper.writeValueAsString(settingsPrompt(query.settings())),
                    objectMapper.writeValueAsString(query.durablePreferences()),
                    objectMapper.writeValueAsString(tastePrompt(query))
            );
            return prompt;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize product-search qualification prompt", exception);
        }
    }

    private SettingsPrompt settingsPrompt(UserSettingsResult settings) {
        UserLocationResult primaryLocation = settings.location();
        return new SettingsPrompt(
                new BudgetPrompt(
                        settings.budget(),
                        "NON_AUTHORITATIVE_DEFAULT_NO_PROVENANCE"
                ),
                UserCurrency.normalizeOrDefault(settings.currency()),
                blankToNull(settings.clothingFit()),
                locationPrompt(primaryLocation),
                safe(settings.locations()).stream()
                        .filter(location -> !location.equals(primaryLocation))
                        .map(this::locationPrompt)
                        .toList(),
                safe(settings.filters()).stream()
                        .map(filter -> new FilterPrompt(filter.id(), filter.label(), filter.description()))
                        .toList()
        );
    }

    private LocationPrompt locationPrompt(UserLocationResult location) {
        if (location == null) {
            return null;
        }
        return new LocationPrompt(
                location.country(),
                location.code(),
                location.region(),
                location.postalCode(),
                location.regionName(),
                location.city()
        );
    }

    private List<TasteSignalPrompt> tastePrompt(GenerateUserProductSearchQualificationQuery query) {
        return safe(query.tasteProfile().signals()).stream()
                .filter(signal -> signal.status() == UserTasteSignalStatus.ACTIVE)
                .map(signal -> new TasteSignalPrompt(
                        signal.signalType(),
                        signal.label(),
                        signal.signalKey(),
                        signal.weight()
                ))
                .toList();
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
                                UserProductSearchDecisionSource.CONVERSATION.name(),
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

    private UserProductSearchQualificationPlan sanitize(ModelResponse response, String currency) {
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
        durableAttributes(durableAttributes, effectiveAttributes);
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
                price(price, currency),
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
                List.of()
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
                        .map(value -> location(value.country(), null, null, "shipsFrom"))
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

    private UserProductSearchQualificationPlan.PriceFilter price(RawPrice raw, String currency) {
        boolean hasValue = raw.minUsd() != null || raw.maxUsd() != null;
        UserProductSearchFilterState state = state(
                raw.relevant(), raw.explicitAny(), hasValue, "price");
        Long min = state == UserProductSearchFilterState.VALUE
                ? minorUnits(raw.minUsd(), currency, "price.minUsd")
                : null;
        Long max = state == UserProductSearchFilterState.VALUE
                ? minorUnits(raw.maxUsd(), currency, "price.maxUsd")
                : null;
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

    private Long minorUnits(BigDecimal value, String currency, String field) {
        BigDecimal normalized = nonNegativeOrNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            int fractionDigits = Currency.getInstance(currency).getDefaultFractionDigits();
            return normalized.movePointRight(fractionDigits < 0 ? 2 : fractionDigits)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw invalid(field + " is outside the supported " + currency + " range");
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
            BudgetPrompt budget,
            String priceCurrency,
            String clothingFit,
            LocationPrompt primaryLocation,
            List<LocationPrompt> otherSavedLocations,
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

    private record BudgetPrompt(Integer value, String authority) {
    }

    private record FilterPrompt(String id, String label, String description) {
    }

    private record TasteSignalPrompt(
            com.meant.api.module.user.constant.UserTasteSignalType type,
            String label,
            String key,
            double weight
    ) {
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
