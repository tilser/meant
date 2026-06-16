package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.entity.ShoppingFilter;
import com.meant.api.module.user.repository.ShoppingFilterRepository;
import com.meant.api.module.user.service.command.ParseUserPreferenceFiltersCommand;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UserPreferenceFilterParsingServiceTest {

    @Test
    void parseSanitizesModelFilterIdsAndUnmappedPreferences() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        UserPreferenceFilterParsingService service = new UserPreferenceFilterParsingService(
                shoppingFilterRepository(),
                openRouterChatClient,
                new OpenRouterProperties(
                        "https://openrouter.test/api/v1",
                        "test-key",
                        "Meant",
                        new OpenRouterProperties.Models("google/gemini-2.5-flash-lite")),
                new ObjectMapper());

        openRouterChatClient.response = """
                {
                  "filterIds": ["organic", "no-polyester", "organic", "unknown-filter"],
                  "unmappedPreferences": ["wide toe box", " ", "wide toe box"]
                }
                """;

        var result = service.parse(new ParseUserPreferenceFiltersCommand(null, "organic and no polyester"));

        assertThat(result.filterIds()).containsExactly("organic", "no-polyester");
        assertThat(result.unmappedPreferences()).containsExactly("wide toe box");
        assertThat(openRouterChatClient.model).isEqualTo("google/gemini-2.5-flash-lite");
        assertThat(openRouterChatClient.schema.properties()).containsKeys("filterIds", "unmappedPreferences");
    }

    private ShoppingFilterRepository shoppingFilterRepository() {
        List<ShoppingFilter> filters = List.of(
                filter("organic", "Organic", 10),
                filter("no-polyester", "No polyester", 20)
        );
        return (ShoppingFilterRepository) Proxy.newProxyInstance(
                ShoppingFilterRepository.class.getClassLoader(),
                new Class<?>[]{ShoppingFilterRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAllByOrderByDisplayOrderAsc" -> filters;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private ShoppingFilter filter(String id, String label, int displayOrder) {
        return ShoppingFilter.builder()
                .id(id)
                .label(label)
                .description(label + " description")
                .category("test")
                .polarity("prefer")
                .displayOrder(displayOrder)
                .createdAt(Instant.now())
                .build();
    }

    static class FakeOpenRouterChatClient extends OpenRouterChatClient {

        private String response;
        private String model;
        private OpenRouterJsonSchemaDefinition schema;

        FakeOpenRouterChatClient() {
            super(RestClient.builder(), new OpenRouterProperties(
                    "https://openrouter.test/api/v1",
                    "test-key",
                    "Meant",
                    new OpenRouterProperties.Models("test-model")));
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
            this.schema = schema;
            return response;
        }
    }
}
