package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchSuggestionsResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchSuggestionServiceTest {

    @Test
    void generateUsesProductSearchQueryParserModelAndActiveFilters() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        openRouterChatClient.response = """
                {
                  "suggestions": [
                    "  Find me organic low-sugar cereal  ",
                    "find me organic low-sugar cereal",
                    "A natural-material T-shirt under $80",
                    "Sustainable dish soap",
                    "Highly rated coffee beans"
                  ]
                }
                """;
        UserProductSearchSuggestionService service = service(openRouterChatClient);

        UserProductSearchSuggestionsResult result = service.generate(upsertCommand());

        assertThat(openRouterChatClient.model).isEqualTo("cheap-query-model");
        assertThat(openRouterChatClient.userPrompt)
                .contains("Organic: Prefer organic food.")
                .contains("No polyester: Avoid polyester.")
                .doesNotContain("$80")
                .doesNotContain("Prague")
                .doesNotContain("Czechia");
        assertThat(openRouterChatClient.schema.properties().get("suggestions").minItems()).isEqualTo(4);
        assertThat(openRouterChatClient.schema.properties().get("suggestions").maxItems()).isEqualTo(4);
        assertThat(result.suggestions()).containsExactly(
                "Find me organic low-sugar cereal",
                "Sustainable dish soap",
                "Highly rated coffee beans",
                "Find me a healthy breakfast cereal"
        );
    }

    @Test
    void generateFiltersPricesPlacesAndLongSuggestions() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        openRouterChatClient.response = """
                {
                  "suggestions": [
                    "Find me a breakfast cereal under 10 US in San Francisco",
                    "Find me cereal near Prague",
                    "Find me a very specific organic low-sugar gluten-free breakfast cereal for a family pantry",
                    "Find me fragrance-free skincare",
                    "Find me local dish soap"
                  ]
                }
                """;
        UserProductSearchSuggestionService service = service(openRouterChatClient);

        UserProductSearchSuggestionsResult result = service.generate(upsertCommand());

        assertThat(result.suggestions()).containsExactly(
                "Find me fragrance-free skincare",
                "Find me a healthy breakfast cereal",
                "Find me a natural-material T-shirt",
                "Find me products that match my filters"
        );
        assertThat(result.suggestions()).allSatisfy(suggestion -> {
            assertThat(suggestion).doesNotContain("$", "Prague", "San Francisco", "local");
            assertThat(suggestion.length()).isLessThanOrEqualTo(64);
        });
    }

    @Test
    void generateReturnsFourSuggestionsAndDoesNotCacheRepeatedRequests() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        openRouterChatClient.response = """
                {
                  "suggestions": ["Organic cereal"]
                }
                """;
        UserProductSearchSuggestionService service = service(openRouterChatClient);

        UserProductSearchSuggestionsResult first = service.generate(upsertCommand());
        UserProductSearchSuggestionsResult second = service.generate(upsertCommand());

        assertThat(openRouterChatClient.calledCount).isEqualTo(2);
        assertThat(first.suggestions()).hasSize(4);
        assertThat(second.suggestions()).hasSize(4);
        assertThat(first.suggestions().getFirst()).isEqualTo("Organic cereal");
    }

    @Test
    void generateFallsBackWhenOpenRouterReturnsBlankResponse() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        openRouterChatClient.response = " ";
        UserProductSearchSuggestionService service = service(openRouterChatClient);

        UserProductSearchSuggestionsResult result = service.generate(upsertCommand());

        assertThat(result.suggestions()).hasSize(4);
        assertThat(result.suggestions()).contains("Find me a healthy breakfast cereal");
    }

    @Test
    void generateFallsBackWhenOpenRouterThrowsException() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient() {
            @Override
            public String completeJson(
                    String model,
                    String systemPrompt,
                    String userPrompt,
                    String schemaName,
                    OpenRouterJsonSchemaDefinition schema
            ) {
                throw new OpenRouterException("Simulated OpenRouter failure");
            }
        };
        UserProductSearchSuggestionService service = service(openRouterChatClient);

        UserProductSearchSuggestionsResult result = service.generate(upsertCommand());

        assertThat(result.suggestions()).hasSize(4);
        assertThat(result.suggestions()).contains("Find me a healthy breakfast cereal");
    }

    private UserProductSearchSuggestionService service(FakeOpenRouterChatClient openRouterChatClient) {
        return new UserProductSearchSuggestionService(
                new FakeUserSettingsService(),
                openRouterChatClient,
                new OpenRouterProperties(
                        "https://openrouter.test/api/v1",
                        "test-key",
                        "Meant",
                        new OpenRouterProperties.Models(
                                "preference-model",
                                "cheap-query-model",
                                "explainer-model",
                                "openrouter/free")),
                new ObjectMapper()
        );
    }

    private UpsertUserCommand upsertCommand() {
        return new UpsertUserCommand(
                UUID.randomUUID(),
                "ada@example.com",
                "Ada",
                "Lovelace"
        );
    }

    static class FakeOpenRouterChatClient extends OpenRouterChatClient {

        private String response;
        private int calledCount;
        private String model;
        private String userPrompt;
        private OpenRouterJsonSchemaDefinition schema;

        FakeOpenRouterChatClient() {
            super(RestClient.builder(), new OpenRouterProperties(
                    "https://openrouter.test/api/v1",
                    "test-key",
                    "Meant",
                    new OpenRouterProperties.Models("test-model", "test-model", "test-model", "test-model")));
        }

        @Override
        public String completeJson(
                String model,
                String systemPrompt,
                String userPrompt,
                String schemaName,
                OpenRouterJsonSchemaDefinition schema
        ) {
            calledCount++;
            this.model = model;
            this.userPrompt = userPrompt;
            this.schema = schema;
            return response;
        }
    }

    static class FakeUserSettingsService extends UserSettingsService {

        FakeUserSettingsService() {
            super(null, null, null, null);
        }

        @Override
        public UserSettingsResult get(UpsertUserCommand upsertCommand) {
            ShoppingFilterResult organic = new ShoppingFilterResult(
                    "organic",
                    "Organic",
                    "Prefer organic food.",
                    "food",
                    "prefer",
                    10
            );
            ShoppingFilterResult noPolyester = new ShoppingFilterResult(
                    "no-polyester",
                    "No polyester",
                    "Avoid polyester.",
                    "materials",
                    "avoid",
                    20
            );
            return new UserSettingsResult(
                    80,
                    new UserLocationResult("Czechia", "CZ", "Prague"),
                    List.of(organic, noPolyester),
                    List.of(organic, noPolyester),
                    List.of(),
                    List.of(),
                    Instant.now(),
                    Instant.now()
            );
        }
    }
}
