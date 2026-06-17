package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.entity.UserProductSearchQueryIntent;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.repository.UserProductSearchQueryIntentRepository;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchQueryUnderstandingServiceTest {

    @Test
    void understandUsesDeterministicParserForSimpleProductQueries() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        UserProductSearchQueryUnderstandingService service = service(openRouterChatClient, queryIntentRepository());

        UserProductSearchQueryIntentResult direct = service.understand("Candles");
        UserProductSearchQueryIntentResult conversational = service.understand(
                "I would like to see some nice candles"
        );

        assertThat(direct.searchQuery()).isEqualTo("candles");
        assertThat(direct.intentCacheKey()).isEqualTo("candles");
        assertThat(direct.confidence()).isEqualTo("high");
        assertThat(conversational.searchQuery()).isEqualTo("candles");
        assertThat(conversational.intentCacheKey()).isEqualTo("candles");
        assertThat(conversational.confidence()).isEqualTo("high");
        assertThat(openRouterChatClient.called).isFalse();
    }

    @Test
    void understandPreservesConcreteConstraintsInDeterministicParser() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        UserProductSearchQueryUnderstandingService service = service(openRouterChatClient, queryIntentRepository());

        UserProductSearchQueryIntentResult result = service.understand("swimming shorts under 50 usd");

        assertThat(result.searchQuery()).isEqualTo("swimming shorts under 50 usd");
        assertThat(result.intentCacheKey()).isEqualTo("swimming shorts under 50 usd");
        assertThat(openRouterChatClient.called).isFalse();
    }

    @Test
    void understandUsesCheapOpenRouterModelForAmbiguousQueriesAndCachesResult() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        openRouterChatClient.response = """
                {
                  "searchQuery": "gift candles",
                  "constraints": [],
                  "preferenceHints": ["for mom"],
                  "confidence": "medium"
                }
                """;
        UserProductSearchQueryIntentRepository repository = queryIntentRepository();
        UserProductSearchQueryUnderstandingService service = service(openRouterChatClient, repository);

        UserProductSearchQueryIntentResult generated = service.understand("birthday gift candles for my mom");
        UserProductSearchQueryIntentResult cached = service.understand("birthday gift candles for my mom");

        assertThat(openRouterChatClient.model).isEqualTo("cheap-query-model");
        assertThat(openRouterChatClient.calledCount).isEqualTo(1);
        assertThat(generated.searchQuery()).isEqualTo("gift candles");
        assertThat(generated.preferenceHints()).containsExactly("for mom");
        assertThat(generated.source()).isEqualTo("llm");
        assertThat(cached.searchQuery()).isEqualTo("gift candles");
        assertThat(cached.preferenceHints()).containsExactly("for mom");
        assertThat(cached.source()).isEqualTo("llm-cache");
    }

    private UserProductSearchQueryUnderstandingService service(
            FakeOpenRouterChatClient openRouterChatClient,
            UserProductSearchQueryIntentRepository repository
    ) {
        return new UserProductSearchQueryUnderstandingService(
                openRouterChatClient,
                new OpenRouterProperties(
                        "https://openrouter.test/api/v1",
                        "test-key",
                        "Meant",
                        new OpenRouterProperties.Models(
                                "preference-model",
                                "cheap-query-model",
                                "explainer-model")),
                new UserProductSearchProperties("v1", "v1", "v1", Duration.ofHours(24)),
                repository,
                new UserProductSearchHashService(new UserProductSearchProperties(
                        "v1",
                        "v1",
                        "v1",
                        Duration.ofHours(24))),
                new ObjectMapper()
        );
    }

    private UserProductSearchQueryIntentRepository queryIntentRepository() {
        Map<String, UserProductSearchQueryIntent> intents = new HashMap<>();
        return (UserProductSearchQueryIntentRepository) Proxy.newProxyInstance(
                UserProductSearchQueryIntentRepository.class.getClassLoader(),
                new Class<?>[]{UserProductSearchQueryIntentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByNormalizedOriginalQueryAndModelAndPromptVersion" -> Optional.ofNullable(
                            intents.get(cacheKey((String) args[0], (String) args[1], (String) args[2]))
                    );
                    case "save" -> {
                        UserProductSearchQueryIntent intent = (UserProductSearchQueryIntent) args[0];
                        intents.put(cacheKey(
                                intent.getNormalizedOriginalQuery(),
                                intent.getModel(),
                                intent.getPromptVersion()
                        ), intent);
                        yield intent;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private String cacheKey(String normalizedOriginalQuery, String model, String promptVersion) {
        return normalizedOriginalQuery + "|" + model + "|" + promptVersion;
    }

    static class FakeOpenRouterChatClient extends OpenRouterChatClient {

        private String response;
        private boolean called;
        private int calledCount;
        private String model;

        FakeOpenRouterChatClient() {
            super(RestClient.builder(), new OpenRouterProperties(
                    "https://openrouter.test/api/v1",
                    "test-key",
                    "Meant",
                    new OpenRouterProperties.Models("test-model", "test-model", "test-model")));
        }

        @Override
        public String completeJson(
                String model,
                String systemPrompt,
                String userPrompt,
                String schemaName,
                OpenRouterJsonSchemaDefinition schema
        ) {
            called = true;
            calledCount++;
            this.model = model;
            return response;
        }
    }
}
