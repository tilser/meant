package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.constant.UserProductCondition;
import com.meant.api.module.user.constant.UserProductPriceTier;
import com.meant.api.module.user.constant.UserProductSearchAttributeName;
import com.meant.api.module.user.constant.UserProductSearchFilterKind;
import com.meant.api.module.user.constant.UserProductSearchFilterState;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchQualificationModelServiceTest {

    @Test
    void alwaysUsesConfiguredLlmAndBuildsEveryTypedFilterDecision() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(validResponse());
        UserProductSearchQualificationModelService service = service(client);

        var result = service.generate(new GenerateUserProductSearchQualificationQuery(
                "trail running shoes",
                "trail running shoes",
                null,
                settings(),
                List.of(new UserProductSearchPreferenceResult(
                        "footwear", UserProductSearchAttributeName.SIZE, List.of("10")))
        ));

        assertThat(client.calls).isEqualTo(1);
        assertThat(client.model).isEqualTo("qualification-model");
        assertThat(client.schema.properties()).containsOnlyKeys(
                "effectiveQuery", "assistantMessage", "suggestedReplies", "available", "condition",
                "shipsTo", "shipsFrom", "price", "shops", "categories", "attributes", "rating",
                "priceTier", "durableAttributes");
        assertThat(client.schema.properties().get("price").properties().get("minUsd").type())
                .isEqualTo(List.of("number", "null"));
        assertThat(client.schema.properties().get("available").properties().get("value").type())
                .isEqualTo(List.of("boolean", "null"));
        assertThat(client.userPrompt).contains("trail running shoes", "United States", "men", "footwear", "10");
        assertThat(client.userPrompt).doesNotContain("budget", "999");

        var plan = result.plan();
        assertThat(plan.effectiveQuery()).isEqualTo("trail running shoes");
        assertThat(plan.available().value()).isTrue();
        assertThat(plan.condition().values()).containsExactly(UserProductCondition.NEW);
        assertThat(plan.shipsTo().value().country()).isEqualTo("US");
        assertThat(plan.price().minUsdMinor()).isEqualTo(5_000L);
        assertThat(plan.price().maxUsdMinor()).isEqualTo(15_000L);
        assertThat(plan.attributes().values())
                .filteredOn(attribute -> attribute.name() == UserProductSearchAttributeName.COLOR)
                .singleElement().satisfies(attribute -> {
                    assertThat(attribute.name()).isEqualTo(UserProductSearchAttributeName.COLOR);
                    assertThat(attribute.values()).containsExactly("Black", "Blue");
                });
        assertThat(plan.attributes().values())
                .filteredOn(attribute -> attribute.name() == UserProductSearchAttributeName.SIZE)
                .singleElement().satisfies(attribute -> assertThat(attribute.values()).containsExactly("10"));
        assertThat(plan.durableAttributes()).singleElement().satisfies(attribute -> {
            assertThat(attribute.scope()).isEqualTo("footwear");
            assertThat(attribute.name()).isEqualTo(UserProductSearchAttributeName.SIZE);
            assertThat(attribute.values()).containsExactly("10");
        });
        assertThat(plan.rating().min()).isEqualByComparingTo("4.5");
        assertThat(plan.rating().minCount()).isEqualTo(10L);
        assertThat(plan.priceTier().values()).containsExactly(UserProductPriceTier.LOW, UserProductPriceTier.MEDIUM);
        assertThat(plan.missingFilters()).containsExactly(UserProductSearchFilterKind.SHIPS_FROM);
        assertThat(result.promptVersion()).isEqualTo("qualification-v1");
    }

    @Test
    void rejectsModelGeneratedTaxonomyReferencesWithoutTrustedResolver() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(validResponse().replace(
                "\"categories\": {\"state\": \"NOT_APPLICABLE\", \"values\": []}",
                "\"categories\": {\"state\": \"VALUE\", "
                        + "\"values\": [\"gid://shopify/TaxonomyCategory/aa-8\"]}"
        ));

        assertThatThrownBy(() -> service(client).generate(new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "running shoes",
                null,
                settings()
        )))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("trusted server resolver");
    }

    @Test
    void rejectsValueDecisionWithoutItsRequiredTypedValue() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(validResponse().replace(
                "\"condition\": {\"state\": \"VALUE\", \"values\": [\"NEW\"]}",
                "\"condition\": {\"state\": \"VALUE\", \"values\": []}"
        ));

        assertThatThrownBy(() -> service(client).generate(new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "running shoes",
                null,
                settings()
        )))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("condition VALUE requires at least one value");
    }

    @Test
    void unresolvedReferenceFiltersCannotKeepQualificationPending() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(validResponse()
                .replace(
                        "\"shops\": {\"state\": \"NOT_APPLICABLE\", \"values\": []}",
                        "\"shops\": {\"state\": \"MISSING\", \"values\": []}")
                .replace(
                        "\"categories\": {\"state\": \"NOT_APPLICABLE\", \"values\": []}",
                        "\"categories\": {\"state\": \"MISSING\", \"values\": []}"));

        var plan = service(client).generate(new GenerateUserProductSearchQualificationQuery(
                "Nike running shoes",
                "Nike running shoes",
                null,
                settings()
        )).plan();

        assertThat(plan.shops().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(plan.categories().state()).isEqualTo(UserProductSearchFilterState.NOT_APPLICABLE);
        assertThat(plan.missingFilters())
                .doesNotContain(UserProductSearchFilterKind.SHOPS, UserProductSearchFilterKind.CATEGORIES);
    }

    @Test
    void preservesKnownAttributesWhileTheAttributeGroupStillNeedsInput() {
        String response = validResponse()
                .replace(
                        "\"attributes\": {\n    \"state\": \"VALUE\"",
                        "\"attributes\": {\n    \"state\": \"MISSING\"")
                .replace(
                        "\"values\": [\n"
                                + "      {\"name\": \"COLOR\", \"values\": [\"Black\", \"Blue\"]},\n"
                                + "      {\"name\": \"SIZE\", \"values\": [\"10\"]}\n"
                                + "    ]",
                        "\"values\": [{\"name\": \"COLOR\", \"values\": [\"Black\"]}]")
                .replace(
                        "\"durableAttributes\": [{\"scope\": \"Footwear\", \"name\": \"SIZE\", "
                                + "\"values\": [\"10\"]}]",
                        "\"durableAttributes\": []");
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(response);

        var plan = service(client).generate(new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "Black, but ask me for anything else you need",
                null,
                settings()
        )).plan();

        assertThat(plan.attributes().state()).isEqualTo(UserProductSearchFilterState.MISSING);
        assertThat(plan.attributes().values()).singleElement().satisfies(attribute -> {
            assertThat(attribute.name()).isEqualTo(UserProductSearchAttributeName.COLOR);
            assertThat(attribute.values()).containsExactly("Black");
        });
        assertThat(plan.missingFilters()).contains(UserProductSearchFilterKind.ATTRIBUTES);
    }

    @Test
    void rejectsDurableSizeThatIsNotInTheEffectiveSizeFilter() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(validResponse().replace(
                "\"durableAttributes\": [{\"scope\": \"Footwear\", \"name\": \"SIZE\", \"values\": [\"10\"]}]",
                "\"durableAttributes\": [{\"scope\": \"Footwear\", \"name\": \"SIZE\", \"values\": [\"11\"]}]"
        ));

        assertThatThrownBy(() -> service(client).generate(new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "11",
                null,
                settings()
        )))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("must match the effective SIZE attribute filter");
    }

    @Test
    void rejectsNonSizeDurableAttributeEvenWhenTheModelBypassesItsSchema() {
        FakeOpenRouterChatClient client = new FakeOpenRouterChatClient(validResponse().replace(
                "\"durableAttributes\": [{\"scope\": \"Footwear\", \"name\": \"SIZE\", \"values\": [\"10\"]}]",
                "\"durableAttributes\": [{\"scope\": \"Footwear\", \"name\": \"COLOR\", "
                        + "\"values\": [\"Black\"]}]"
        ));

        assertThatThrownBy(() -> service(client).generate(new GenerateUserProductSearchQualificationQuery(
                "running shoes",
                "black",
                null,
                settings()
        )))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("only SIZE may be a durable");
    }

    private UserProductSearchQualificationModelService service(FakeOpenRouterChatClient client) {
        return new UserProductSearchQualificationModelService(
                client,
                new OpenRouterProperties(
                        "https://openrouter.test/api/v1",
                        "test-key",
                        "Meant",
                        new OpenRouterProperties.Models(
                                "preference-model",
                                "qualification-model",
                                "explanation-model",
                                "chat-model"
                        )
                ),
                new UserProductSearchProperties(
                        "search-v1",
                        "qualification-v1",
                        "explanation-v1",
                        Duration.ofMinutes(1),
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
                ),
                new ObjectMapper()
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

    private String validResponse() {
        return """
                {
                  "effectiveQuery": "trail running shoes",
                  "assistantMessage": "Which size do you need?",
                  "suggestedReplies": ["10", "10.5", "Any size"],
                  "available": {"state": "VALUE", "value": true},
                  "condition": {"state": "VALUE", "values": ["NEW"]},
                  "shipsTo": {"state": "VALUE", "country": "US", "region": "", "postalCode": ""},
                  "shipsFrom": {"state": "MISSING", "values": []},
                  "price": {"state": "VALUE", "minUsd": 50, "maxUsd": 150},
                  "shops": {"state": "NOT_APPLICABLE", "values": []},
                  "categories": {"state": "NOT_APPLICABLE", "values": []},
                  "attributes": {
                    "state": "VALUE",
                    "values": [
                      {"name": "COLOR", "values": ["Black", "Blue"]},
                      {"name": "SIZE", "values": ["10"]}
                    ]
                  },
                  "rating": {"state": "VALUE", "min": 4.5, "minCount": 10},
                  "priceTier": {"state": "VALUE", "values": ["LOW", "MEDIUM"]},
                  "durableAttributes": [{"scope": "Footwear", "name": "SIZE", "values": ["10"]}]
                }
                """;
    }

    private static final class FakeOpenRouterChatClient extends OpenRouterChatClient {

        private final String response;
        private int calls;
        private String model;
        private String userPrompt;
        private OpenRouterJsonSchemaDefinition schema;

        private FakeOpenRouterChatClient(String response) {
            super(RestClient.builder(), new OpenRouterProperties(
                    "https://openrouter.test/api/v1",
                    "test-key",
                    "Meant",
                    new OpenRouterProperties.Models("test", "test", "test", "test")
            ));
            this.response = response;
        }

        @Override
        public String completeJson(
                String model,
                String systemPrompt,
                String userPrompt,
                String schemaName,
                OpenRouterJsonSchemaDefinition schema
        ) {
            calls++;
            this.model = model;
            this.userPrompt = userPrompt;
            this.schema = schema;
            return response;
        }
    }
}
