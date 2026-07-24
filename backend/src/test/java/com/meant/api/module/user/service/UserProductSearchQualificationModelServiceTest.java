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
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
                .contains("blue jeans", "United States", "men")
                .doesNotContain("budget", "999");

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
    void repairsAnyThatHasNoExplicitUserEvidenceInsteadOfAuthorizingReady() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(
                unsupportedRatingAnyResponse(),
                missingRatingRepairResponse()
        );

        String request = "new desk lamp under $100 shipped to US from CA, low price tier";
        var plan = service(client).generate(query(request, request, null)).plan();

        assertThat(client.calls).isEqualTo(2);
        assertThat(client.userPrompts.get(1))
                .contains(
                        "Server validation rejected the previous assessment",
                        "RATING ANY has no user-context provenance",
                        "questionTargets must exactly cover unresolved targets"
                );
        assertThat(plan.rating().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(plan.questionTargets()).containsExactly(UserProductSearchQuestionTarget.RATING);
        assertThat(plan.missingTargets()).containsExactly(UserProductSearchQuestionTarget.RATING);
        assertThat(plan.assistantMessage()).isEqualTo("What minimum rating do you want?");
    }

    @Test
    void acceptsCategorySpecificIrrelevanceWithoutTurningItIntoAMissingFilter() {
        String categorySpecific = combinedQuestionResponse().replaceFirst(
                "(?s)\"condition\"\\s*:\\s*\\{\\s*\"relevant\"\\s*:\\s*true",
                "\"condition\": {\"relevant\": false"
        );
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(categorySpecific, categorySpecific);

        var plan = service(client).generate(query("blue jeans", "blue jeans", null)).plan();

        assertThat(client.calls).isEqualTo(2);
        assertThat(plan.condition().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(plan.missingTargets()).doesNotContain(UserProductSearchQuestionTarget.CONDITION);
        assertThat(plan.missingFilters()).doesNotContain(UserProductSearchFilterKind.CONDITION);
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
    void modelFailureForAnUnclassifiedSearchFailsClosed() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient("not-json", "still-not-json");

        assertThatThrownBy(() -> service(client).generate(query("desk lamp", "desk lamp", null)))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("no conservative category fallback");

        assertThat(client.calls).isEqualTo(2);
    }

    @Test
    void modelFailureForFootballBootsConservativelyRequiresSizeAndDestination() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient("not-json", "still-not-json");
        UserSettingsResult noLocation = new UserSettingsResult(
                null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                Instant.EPOCH, Instant.EPOCH);
        GenerateUserProductSearchQualificationQuery query = new GenerateUserProductSearchQualificationQuery(
                "football boots", "football boots", null, noLocation, List.of());

        var plan = service(client).generate(query).plan();

        assertThat(client.calls).isEqualTo(2);
        assertThat(plan.missingTargets()).containsExactlyInAnyOrder(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        );
        assertThat(plan.assistantMessage()).contains("boot size", "ship to");
    }

    @Test
    void modelFailureNeverDropsExplicitColorOrTargetGenderConstraints() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient("not-json", "still-not-json");

        assertThatThrownBy(() -> service(client).generate(query(
                "black mens football boots",
                "black mens football boots",
                null
        )))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("no conservative category fallback");
        assertThat(client.calls).isEqualTo(2);
    }

    @Test
    void modelFailureCanUseProfileDestinationAndScopedDurableBootSize() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient("not-json", "still-not-json");
        GenerateUserProductSearchQualificationQuery request =
                new GenerateUserProductSearchQualificationQuery(
                        "football boots",
                        "football boots",
                        null,
                        settings(),
                        List.of(new UserProductSearchPreferenceResult(
                                "football-boots",
                                UserProductSearchAttributeName.SIZE,
                                List.of("10")
                        ))
                );

        var plan = service(client).generate(request).plan();

        assertThat(plan.missingTargets()).isEmpty();
        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE).values()).containsExactly("10");
        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE).provenance().source())
                .isEqualTo(UserProductSearchDecisionSource.DURABLE_PREFERENCE);
        assertThat(plan.shipsTo().value().country()).isEqualTo("US");
    }

    @Test
    void repairsAnAttributeDecisionWhenTheRequiredRelevanceFlagIsMissing() {
        String missingSizeRelevance = combinedQuestionResponse().replace(
                "\"name\": \"SIZE\", \"relevant\": true, \"explicitAny\": false",
                "\"name\": \"SIZE\", \"explicitAny\": false"
        );
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(
                missingSizeRelevance,
                combinedQuestionResponse()
        );

        var plan = service(client).generate(query("blue jeans", "blue jeans", null)).plan();

        assertThat(client.calls).isEqualTo(2);
        assertThat(client.userPrompts.get(1)).contains(
                "Server validation rejected the previous assessment",
                "attributes.SIZE.relevant is required"
        );
        assertThat(attribute(plan, UserProductSearchAttributeName.SIZE).state())
                .isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(plan.questionTargets()).contains(UserProductSearchQuestionTarget.SIZE);
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
                new UserProductSearchQualificationPlanResolver()
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
                    "provenance": {"source": "ORIGINAL_QUERY", "evidence": "under $100"},
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

        private final List<String> responses;
        private final List<String> userPrompts = new ArrayList<>();
        private int calls;
        private String model;
        private OpenRouterJsonSchemaDefinition schema;

        private FakeOpenRouterChatClient(String... responses) {
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
                OpenRouterJsonSchemaDefinition schema
        ) {
            this.model = model;
            this.userPrompts.add(userPrompt);
            this.schema = schema;
            return responses.get(calls++);
        }
    }
}
