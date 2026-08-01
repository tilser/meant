package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import com.meant.api.module.user.constant.UserProductSearchFilterKind;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchConversationMessage;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchQualificationModelServiceTest {

    @Test
    void buildsOneCombinedQuestionForEveryMissingDecisionAndKeepsAttributeStatesIndependent() throws Exception {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(combinedQuestionResponse());

        var result = service(client).generate(query("blue jeans", "blue jeans", null));

        assertThat(client.calls).isEqualTo(1);
        assertThat(client.model).isEqualTo("chat-model");
        assertThat(client.schema.properties()).containsOnlyKeys(
                "effectiveQuery", "assistantMessage", "suggestedReplies", "questionTargets", "condition",
                "shipsTo", "shipsFrom", "price", "attributes", "rating", "priceTier", "durableAttributes");
        assertThat(client.schema.properties()).doesNotContainKeys("available", "shops", "categories");
        assertThat(client.schema.properties().get("questionTargets").items().enumValues())
                .containsExactlyElementsOf(java.util.Arrays.stream(UserProductSearchQuestionTarget.values())
                        .map(Enum::name)
                        .toList());
        assertThat(new ObjectMapper().writeValueAsString(client.schema)).doesNotContain("minItems", "maxItems");
        assertThat(client.userPrompts.getFirst())
                .contains(
                        "blue jeans",
                        "United States",
                        "men",
                        "\"priceCurrency\":\"USD\"",
                        "\"budget\":{\"value\":999,\"authority\":\"NON_AUTHORITATIVE_DEFAULT_NO_PROVENANCE\"}"
                );

        var plan = result.plan();
        assertThat(plan.currentSchema()).isTrue();
        assertThat(plan.available().state()).isEqualTo(UserProductSearchFilterState.VALUE);
        assertThat(plan.available().value()).isTrue();
        assertThat(plan.available().provenance().source())
                .isEqualTo(UserProductSearchDecisionSource.SYSTEM_POLICY);
        assertThat(plan.shops().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(plan.categories().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(plan.questionTargets()).containsExactly(
                UserProductSearchQuestionTarget.CONDITION,
                UserProductSearchQuestionTarget.SHIPS_FROM,
                UserProductSearchQuestionTarget.PRICE,
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.RATING,
                UserProductSearchQuestionTarget.PRICE_TIER
        );
        assertThat(plan.missingTargets()).containsExactlyElementsOf(plan.questionTargets());
        assertThat(plan.missingFilters()).containsExactly(
                UserProductSearchFilterKind.CONDITION,
                UserProductSearchFilterKind.SHIPS_FROM,
                UserProductSearchFilterKind.PRICE,
                UserProductSearchFilterKind.ATTRIBUTES,
                UserProductSearchFilterKind.RATING,
                UserProductSearchFilterKind.PRICE_TIER
        );
        assertThat(attribute(plan, UserProductSearchAttributeName.COLOR)).satisfies(attribute -> {
            assertThat(attribute.state()).isEqualTo(UserProductSearchFilterState.VALUE);
            assertThat(attribute.values()).containsExactly("Blue");
            assertThat(attribute.provenance().source()).isEqualTo(UserProductSearchDecisionSource.ORIGINAL_QUERY);
        });
        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE)).satisfies(attribute -> {
            assertThat(attribute.state()).isEqualTo(UserProductSearchFilterState.MISSING);
            assertThat(attribute.values()).isEmpty();
        });
        assertThat(attribute(plan, UserProductSearchAttributeName.TARGET_GENDER)).satisfies(attribute -> {
            assertThat(attribute.state()).isEqualTo(UserProductSearchFilterState.VALUE);
            assertThat(attribute.values()).containsExactly("Male");
            assertThat(attribute.provenance().source()).isEqualTo(UserProductSearchDecisionSource.PROFILE);
        });
        assertThat(result.model()).isEqualTo("chat-model");
        assertThat(result.promptVersion()).isEqualTo("qualification-v1");
    }

    @Test
    void suppliesTheAccountCurrencyAsTrustedQualificationContext() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(combinedQuestionResponse());
        GenerateUserProductSearchQualificationQuery request = new GenerateUserProductSearchQualificationQuery(
                "blue jeans",
                "blue jeans",
                null,
                settingsWithCurrency("EUR"),
                List.of()
        );

        service(client).generate(request);

        assertThat(client.userPrompts.getFirst()).contains("\"priceCurrency\":\"EUR\"");
    }

    @Test
    void convertsModelPriceBoundsUsingTheAccountCurrenciesMinorUnitExponent() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(
                missingRatingRepairResponse().replace("100 USD", "100 JPY")
        );
        String request = "new desk lamp under 100 JPY shipped to US from CA, low price tier";
        GenerateUserProductSearchQualificationQuery query = new GenerateUserProductSearchQualificationQuery(
                request,
                request,
                null,
                settingsWithCurrency("JPY"),
                List.of()
        );

        UserProductSearchQualificationPlan plan = service(client).generate(query).plan();

        assertThat(plan.price().maxUsdMinor()).isEqualTo(100L);
    }

    @Test
    void labelsThePrimaryAndSecondarySavedLocationsSeparatelyInTheModelPrompt() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(combinedQuestionResponse());
        UserLocationResult primary = new UserLocationResult("United States", "US", "San Francisco");
        UserLocationResult secondary = new UserLocationResult("Czech Republic", "CZ", "Prague");
        UserSettingsResult base = settings();
        UserSettingsResult settings = new UserSettingsResult(
                base.budget(),
                base.clothingFit(),
                primary,
                List.of(primary, secondary),
                base.filters(),
                base.availableFilters(),
                base.parsedFilterIds(),
                base.unmappedPreferences(),
                base.createdAt(),
                base.updatedAt()
        );
        GenerateUserProductSearchQualificationQuery query =
                new GenerateUserProductSearchQualificationQuery(
                        "blue jeans", "blue jeans", null, settings, List.of());

        service(client).generate(query);

        assertThat(client.userPrompts.getFirst())
                .contains(
                        "\"primaryLocation\":{\"country\":\"United States\",\"code\":\"US\"",
                        "\"otherSavedLocations\":[{\"country\":\"Czech Republic\",\"code\":\"CZ\""
                );
    }

    @Test
    void suppliesTrustedConversationCompleteSearchContractAndActiveTasteAsSoftContext() throws Exception {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(combinedQuestionResponse());
        var conversation = new ArrayList<>(List.of(
                new UserProductSearchConversationMessage(
                        UserProductSearchConversationMessage.Role.USER,
                        "I prefer understated designs."
                ),
                new UserProductSearchConversationMessage(
                        UserProductSearchConversationMessage.Role.ASSISTANT,
                        "I will keep that in mind."
                ),
                new UserProductSearchConversationMessage(
                        UserProductSearchConversationMessage.Role.USER,
                        "blue jeans"
                )
        ));
        var request = new GenerateUserProductSearchQualificationQuery(
                "blue jeans",
                "blue jeans",
                null,
                settings(),
                List.of(),
                conversation,
                new UserTasteProfileResult(
                        "taste-profile",
                        List.of(
                                tasteSignal("minimal", "Minimal", 1.5d, UserTasteSignalStatus.ACTIVE),
                                tasteSignal("hidden", "Disabled taste", 9.0d, UserTasteSignalStatus.DISABLED)
                        ),
                        List.of()
                )
        );
        conversation.clear();

        service(client).generate(request);

        String prompt = client.userPrompts.getFirst();
        assertThat(prompt).contains(
                "Supported Shopify/UCP search-parameter contract",
                "TEST_RUNTIME_VERIFIED_SEARCH_CONTRACT",
                "I prefer understated designs.",
                "\"role\":\"ASSISTANT\",\"text\":\"I will keep that in mind.\"",
                "\"type\":\"BRAND\",\"label\":\"Minimal\",\"key\":\"minimal\",\"weight\":1.5"
        ).doesNotContain("Disabled taste", "positiveCount", "lastBehavior");
        assertThat(new ObjectMapper().writeValueAsString(client.schema))
                .contains(UserProductSearchDecisionSource.CONVERSATION.name());
        assertThat(request.conversation()).hasSize(3);
    }

    @Test
    void acceptsFirstStructurallyValidModelResultWithoutSemanticChecksOrRepair() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(unsupportedRatingAnyResponse());

        var plan = service(client).generate(query(
                "new desk lamp under 100 USD shipped to US from CA, low price tier",
                "new desk lamp under 100 USD shipped to US from CA, low price tier",
                null
        )).plan();

        assertThat(client.calls).isEqualTo(1);
        assertThat(plan.rating().state()).isEqualTo(UserProductSearchFilterState.ANY);
        assertThat(plan.rating().provenance().source()).isEqualTo(UserProductSearchDecisionSource.NONE);
        assertThat(plan.questionTargets()).isEmpty();
        assertThat(plan.assistantMessage()).isEqualTo("Ready to search.");
    }

    @Test
    void fallsBackToAnUnqualifiedSearchAfterStructurallyInvalidJsonWithoutRetry() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient("not-json", combinedQuestionResponse());

        var plan = service(client).generate(query("desk lamp", "desk lamp", null)).plan();

        assertThat(client.calls).isEqualTo(1);
        assertThat(plan.effectiveQuery()).isEqualTo("desk lamp");
        assertThat(plan.missingFilters()).isEmpty();
        assertThat(plan.missingTargets()).isEmpty();
        assertThat(plan.durableAttributes()).isEmpty();
        assertThat(plan.condition().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(plan.price().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
    }

    @Test
    void fallsBackToAnUnqualifiedSearchAfterTheOnlyModelCallFails() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(
                new OpenRouterException("OpenRouter timed out"),
                combinedQuestionResponse()
        );

        var plan = service(client).generate(query("desk lamp", "desk lamp", null)).plan();

        assertThat(client.calls).isEqualTo(1);
        assertThat(plan.effectiveQuery()).isEqualTo("desk lamp");
        assertThat(plan.missingTargets()).isEmpty();
        assertThat(plan.attributes().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
    }

    @Test
    void acceptsCategorySpecificIrrelevanceWithoutTurningItIntoAMissingFilter() {
        String categorySpecific = combinedQuestionResponse().replaceFirst(
                "(?s)\"condition\"\\s*:\\s*\\{\\s*\"relevant\"\\s*:\\s*true",
                "\"condition\": {\"relevant\": false"
        );
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(categorySpecific, categorySpecific);

        var plan = service(client).generate(query("blue jeans", "blue jeans", null)).plan();

        assertThat(client.calls).isEqualTo(1);
        assertThat(plan.condition().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(plan.missingTargets()).doesNotContain(UserProductSearchQuestionTarget.CONDITION);
        assertThat(plan.missingFilters()).doesNotContain(UserProductSearchFilterKind.CONDITION);
    }

    @Test
    void keepsBuyerOriginDetailInEffectiveQueryButSanitizesShipsFromToCountryOnly() throws Exception {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(originCountryOnlyResponse());
        String request = "new desk lamp under 100 USD shipped to US from CA BC 90210, low price tier";

        var plan = service(client).generate(query(request, request, null)).plan();

        assertThat(plan.effectiveQuery()).contains("from CA BC 90210");
        assertThat(plan.shipsFrom().values()).singleElement().satisfies(origin -> {
            assertThat(origin.country()).isEqualTo("CA");
            assertThat(origin.region()).isNull();
            assertThat(origin.postalCode()).isNull();
        });
    }

    @Test
    void foodSearchDoesNotAskForFootwearOrGenericCommerceFilters() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(foodResponse());

        var plan = service(client).generate(query(
                "gluten-free pasta delivered to the United States",
                "gluten-free pasta delivered to the United States",
                null
        )).plan();

        assertThat(client.calls).isEqualTo(1);
        assertThat(plan.missingTargets()).isEmpty();
        assertThat(plan.effectiveQuery()).isEqualTo("gluten-free pasta");
        assertThat(plan.shipsTo().state()).isEqualTo(UserProductSearchFilterState.VALUE);
        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(attribute(plan, UserProductSearchAttributeName.TARGET_GENDER).state())
                .isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(plan.condition().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(plan.price().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(plan.rating().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
    }

    @Test
    void ignoresAResultMissingRequiredStructuralFieldsWithoutRetry() {
        String missingSizeRelevance = combinedQuestionResponse().replace(
                "\"name\": \"SIZE\", \"relevant\": true, \"explicitAny\": false",
                "\"name\": \"SIZE\", \"explicitAny\": false"
        );
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(
                missingSizeRelevance,
                combinedQuestionResponse()
        );

        var plan = service(client).generate(query("blue jeans", "blue jeans", null)).plan();

        assertThat(client.calls).isEqualTo(1);
        assertThat(plan.effectiveQuery()).isEqualTo("blue jeans");
        assertThat(plan.attributes().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
    }

    private UserProductSearchQualificationPlan.Attribute attribute(
            UserProductSearchQualificationPlan plan,
            UserProductSearchAttributeName name
    ) {
        return plan.attributes().values().stream()
                .filter(attribute -> attribute.name() == name)
                .findFirst()
                .orElseThrow();
    }

    private UserProductSearchQualificationModelService service(FakeOpenRouterChatClient client) {
        return new UserProductSearchQualificationModelService(
                client,
                properties(),
                searchProperties(),
                new ObjectMapper(),
                () -> "TEST_RUNTIME_VERIFIED_SEARCH_CONTRACT"
        );
    }

    private GenerateUserProductSearchQualificationQuery query(
            String originalQuery,
            String message,
            UserProductSearchQualificationPlan previous
    ) {
        return new GenerateUserProductSearchQualificationQuery(
                originalQuery,
                message,
                previous,
                settings(),
                List.of()
        );
    }

    private OpenRouterProperties properties() {
        return new OpenRouterProperties(
                "https://openrouter.test/api/v1",
                "test-key",
                "Meant",
                new OpenRouterProperties.Models(
                        "preference-model",
                        "qualification-model",
                        "explanation-model",
                        "chat-model"
                )
        );
    }

    private UserProductSearchProperties searchProperties() {
        return new UserProductSearchProperties(
                "search-v1",
                "qualification-v1",
                "explanation-v1",
                4096,
                Duration.ofMinutes(1),
                Duration.ofMinutes(30),
                Duration.ofMinutes(1),
                100,
                Duration.ofSeconds(30),
                10,
                5,
                5,
                Duration.ofDays(1),
                Duration.ofDays(7),
                10,
                2,
                80
        );
    }

    private UserSettingsResult settings() {
        UserLocationResult location = new UserLocationResult("United States", "US", "New York");
        return new UserSettingsResult(
                999,
                "men",
                location,
                List.of(location),
                List.of(new ShoppingFilterResult(
                        "durable-construction",
                        "Durable construction",
                        "Prefer products built for long-term durability.",
                        "materials",
                        "prefer",
                        10
                )),
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-07-17T10:00:00Z"),
                Instant.parse("2026-07-17T10:00:00Z")
        );
    }

    private UserSettingsResult settingsWithCurrency(String currency) {
        UserSettingsResult base = settings();
        return new UserSettingsResult(
                base.budget(),
                currency,
                base.clothingFit(),
                base.location(),
                base.locations(),
                base.filters(),
                base.availableFilters(),
                base.parsedFilterIds(),
                base.unmappedPreferences(),
                base.createdAt(),
                base.updatedAt()
        );
    }

    private UserTasteSignalResult tasteSignal(
            String key,
            String label,
            double weight,
            UserTasteSignalStatus status
    ) {
        return new UserTasteSignalResult(
                UUID.randomUUID(),
                UserTasteSignalType.BRAND,
                key,
                label,
                weight,
                1,
                0,
                "SAVE",
                null,
                UserTasteSuggestionStatus.PENDING,
                status,
                Instant.parse("2026-07-17T10:00:00Z"),
                Instant.parse("2026-07-17T10:00:00Z")
        );
    }

    private String combinedQuestionResponse() {
        return """
                {
                  "effectiveQuery": "blue jeans",
                  "assistantMessage": "Please provide condition shipping origin budget size rating price tier preferences.",
                  "suggestedReplies": [],
                  "questionTargets": ["CONDITION", "SHIPS_FROM", "PRICE", "SIZE", "RATING", "PRICE_TIER"],
                  "condition": {
                    "relevant": true, "explicitAny": false,
                    "provenance": {"source": "NONE", "evidence": ""}, "values": []
                  },
                  "shipsTo": {
                    "relevant": true, "explicitAny": false,
                    "provenance": {"source": "PROFILE", "evidence": "US"},
                    "country": "US", "region": null, "postalCode": null
                  },
                  "shipsFrom": {
                    "relevant": true, "explicitAny": false,
                    "provenance": {"source": "NONE", "evidence": ""}, "values": []
                  },
                  "price": {
                    "relevant": true, "explicitAny": false,
                    "provenance": {"source": "NONE", "evidence": ""}, "minUsd": null, "maxUsd": null
                  },
                  "attributes": [
                    {
                      "name": "COLOR", "relevant": true, "explicitAny": false,
                      "provenance": {"source": "ORIGINAL_QUERY", "evidence": "blue"}, "values": ["Blue"]
                    },
                    {
                      "name": "SIZE", "relevant": true, "explicitAny": false,
                      "provenance": {"source": "NONE", "evidence": ""}, "values": []
                    },
                    {
                      "name": "TARGET_GENDER", "relevant": true, "explicitAny": false,
                      "provenance": {"source": "PROFILE", "evidence": "men"}, "values": ["Male"]
                    }
                  ],
                  "rating": {
                    "relevant": true, "explicitAny": false,
                    "provenance": {"source": "NONE", "evidence": ""}, "min": null, "minCount": null
                  },
                  "priceTier": {
                    "relevant": true, "explicitAny": false,
                    "provenance": {"source": "NONE", "evidence": ""}, "values": []
                  },
                  "durableAttributes": []
                }
                """;
    }

    private String unsupportedRatingAnyResponse() {
        return """
                {
                  "effectiveQuery": "desk lamp",
                  "assistantMessage": "Ready to search.",
                  "suggestedReplies": [],
                  "questionTargets": [],
                  "condition": {"relevant": true, "explicitAny": false,
                    "provenance": {"source": "ORIGINAL_QUERY", "evidence": "new"}, "values": ["NEW"]},
                  "shipsTo": {"relevant": true, "explicitAny": false,
                    "provenance": {"source": "ORIGINAL_QUERY", "evidence": "shipped to US"},
                    "country": "US", "region": null, "postalCode": null},
                  "shipsFrom": {"relevant": true, "explicitAny": false,
                    "provenance": {"source": "ORIGINAL_QUERY", "evidence": "from CA"},
                    "values": [{"country": "CA", "region": null, "postalCode": null}]},
                  "price": {"relevant": true, "explicitAny": false,
                    "provenance": {"source": "ORIGINAL_QUERY", "evidence": "under 100 USD"},
                    "minUsd": null, "maxUsd": 100},
                  "attributes": [
                    {"name": "COLOR", "relevant": false, "explicitAny": false,
                      "provenance": {"source": "NONE", "evidence": ""}, "values": []},
                    {"name": "SIZE", "relevant": false, "explicitAny": false,
                      "provenance": {"source": "NONE", "evidence": ""}, "values": []},
                    {"name": "TARGET_GENDER", "relevant": false, "explicitAny": false,
                      "provenance": {"source": "NONE", "evidence": ""}, "values": []}
                  ],
                  "rating": {"relevant": true, "explicitAny": true,
                    "provenance": {"source": "NONE", "evidence": ""}, "min": null, "minCount": null},
                  "priceTier": {"relevant": true, "explicitAny": false,
                    "provenance": {"source": "ORIGINAL_QUERY", "evidence": "low price tier"},
                    "values": ["LOW"]},
                  "durableAttributes": []
                }
                """;
    }

    private String missingRatingRepairResponse() {
        return unsupportedRatingAnyResponse()
                .replace("\"assistantMessage\": \"Ready to search.\"",
                        "\"assistantMessage\": \"What minimum rating do you want?\"")
                .replace("\"questionTargets\": []", "\"questionTargets\": [\"RATING\"]")
                .replace("\"rating\": {\"relevant\": true, \"explicitAny\": true",
                        "\"rating\": {\"relevant\": true, \"explicitAny\": false");
    }

    private String originCountryOnlyResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var response = (tools.jackson.databind.node.ObjectNode) mapper.readTree(unsupportedRatingAnyResponse());
        response.put("effectiveQuery", "desk lamp from CA BC 90210");
        var shipsFrom = (tools.jackson.databind.node.ObjectNode) response.get("shipsFrom");
        ((tools.jackson.databind.node.ObjectNode) shipsFrom.get("provenance"))
                .put("evidence", "from CA BC 90210");
        var origin = (tools.jackson.databind.node.ObjectNode) shipsFrom.get("values").get(0);
        origin.put("region", "BC");
        origin.put("postalCode", "90210");
        var rating = (tools.jackson.databind.node.ObjectNode) response.get("rating");
        rating.put("relevant", false);
        rating.put("explicitAny", false);
        return mapper.writeValueAsString(response);
    }

    private String foodResponse() {
        return """
                {
                  "effectiveQuery": "gluten-free pasta",
                  "assistantMessage": "Ready to search for gluten-free pasta.",
                  "suggestedReplies": [],
                  "questionTargets": [],
                  "condition": {"relevant": false, "explicitAny": false,
                    "provenance": {"source": "NONE", "evidence": ""}, "values": []},
                  "shipsTo": {"relevant": true, "explicitAny": false,
                    "provenance": {"source": "ORIGINAL_QUERY", "evidence": "United States"},
                    "country": "US", "region": null, "postalCode": null},
                  "shipsFrom": {"relevant": false, "explicitAny": false,
                    "provenance": {"source": "NONE", "evidence": ""}, "values": []},
                  "price": {"relevant": false, "explicitAny": false,
                    "provenance": {"source": "NONE", "evidence": ""},
                    "minUsd": null, "maxUsd": null},
                  "attributes": [
                    {"name": "COLOR", "relevant": false, "explicitAny": false,
                      "provenance": {"source": "NONE", "evidence": ""}, "values": []},
                    {"name": "SIZE", "relevant": false, "explicitAny": false,
                      "provenance": {"source": "NONE", "evidence": ""}, "values": []},
                    {"name": "TARGET_GENDER", "relevant": false, "explicitAny": false,
                      "provenance": {"source": "NONE", "evidence": ""}, "values": []}
                  ],
                  "rating": {"relevant": false, "explicitAny": false,
                    "provenance": {"source": "NONE", "evidence": ""},
                    "min": null, "minCount": null},
                  "priceTier": {"relevant": false, "explicitAny": false,
                    "provenance": {"source": "NONE", "evidence": ""}, "values": []},
                  "durableAttributes": []
                }
                """;
    }

    private static final class FakeOpenRouterChatClient extends OpenRouterChatClient {

        private final List<Object> responses;
        private final List<String> userPrompts = new ArrayList<>();
        private final List<String> models = new ArrayList<>();
        private final List<Integer> maximumOutputTokens = new ArrayList<>();
        private int calls;
        private String model;
        private OpenRouterJsonSchemaDefinition schema;

        private FakeOpenRouterChatClient(Object... responses) {
            super(RestClient.builder(), new OpenRouterProperties(
                    "https://openrouter.test/api/v1",
                    "test-key",
                    "Meant",
                    new OpenRouterProperties.Models("test", "test", "test", "test")
            ));
            this.responses = List.of(responses);
        }

        @Override
        public String completeJson(
                String model,
                String systemPrompt,
                String userPrompt,
                String schemaName,
                OpenRouterJsonSchemaDefinition schema,
                int maximumOutputTokens
        ) {
            this.model = model;
            this.models.add(model);
            this.userPrompts.add(userPrompt);
            this.maximumOutputTokens.add(maximumOutputTokens);
            this.schema = schema;
            Object response = responses.get(calls++);
            if (response instanceof RuntimeException exception) {
                throw exception;
            }
            return (String) response;
        }
    }
}
