package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.exception.OpenRouterException;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.entity.UserProductSearchEvent;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.repository.UserProductSearchEventRepository;
import com.meant.api.module.user.service.dto.UserPopularProductSearchResult;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchEventServiceTest {

    @Test
    void popularPolishesAggregateResultsBeforeReturningThem() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        openRouterChatClient.response = """
                {
                  "searches": [
                    {
                      "displayQuery": "Scented candles under $25",
                      "query": "Scented candles under $25"
                    }
                  ]
                }
                """;
        UserProductSearchEventService service = service(
                openRouterChatClient,
                List.of(new UserPopularProductSearchResult(
                        "birthday gift candles for my mom Sarah",
                        "birthday gift candles for my mom Sarah",
                        8L,
                        4L,
                        Instant.now()
                ))
        );

        List<UserPopularProductSearchResult> result = service.popular(Instant.now());

        assertThat(openRouterChatClient.model).isEqualTo("openrouter/free");
        assertThat(result).singleElement()
                .satisfies(search -> {
                    assertThat(search.displayQuery()).isEqualTo("Scented candles under $25");
                    assertThat(search.query()).isEqualTo("Scented candles under $25");
                });
    }

    @Test
    void popularReturnsEmptyWhenPolishFailsSoRawQueriesAreNotExposed() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        openRouterChatClient.fail = true;
        UserProductSearchEventService service = service(
                openRouterChatClient,
                List.of(new UserPopularProductSearchResult(
                        "birthday gift candles for my mom Sarah",
                        "birthday gift candles for my mom Sarah",
                        8L,
                        4L,
                        Instant.now()
                ))
        );

        List<UserPopularProductSearchResult> result = service.popular(Instant.now());

        assertThat(result).isEmpty();
    }

    @Test
    void popularDropsPolishedResultsWithFirstPersonPhrasing() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        openRouterChatClient.response = """
                {
                  "searches": [
                    {
                      "displayQuery": "Candles for my mom",
                      "query": "Candles for my mom"
                    },
                    {
                      "displayQuery": "Scented candles under $25",
                      "query": "Scented candles under $25"
                    }
                  ]
                }
                """;
        UserProductSearchEventService service = service(
                openRouterChatClient,
                List.of(new UserPopularProductSearchResult(
                        "birthday gift candles for my mom Sarah",
                        "birthday gift candles for my mom Sarah",
                        8L,
                        4L,
                        Instant.now()
                ))
        );

        List<UserPopularProductSearchResult> result = service.popular(Instant.now());

        assertThat(result).singleElement()
                .satisfies(search -> assertThat(search.displayQuery()).isEqualTo("Scented candles under $25"));
    }

    @Test
    void popularSkipsPolishWhenThereAreNoAggregateResults() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        UserProductSearchEventService service = service(openRouterChatClient, List.of());

        List<UserPopularProductSearchResult> result = service.popular(Instant.now());

        assertThat(result).isEmpty();
        assertThat(openRouterChatClient.called).isFalse();
    }

    private UserProductSearchEventService service(
            FakeOpenRouterChatClient openRouterChatClient,
            List<UserPopularProductSearchResult> popularResults
    ) {
        return new UserProductSearchEventService(
                repository(popularResults),
                properties(),
                openRouterProperties(),
                openRouterChatClient,
                new ObjectMapper()
        );
    }

    private UserProductSearchEventRepository repository(List<UserPopularProductSearchResult> popularResults) {
        return (UserProductSearchEventRepository) Proxy.newProxyInstance(
                UserProductSearchEventRepository.class.getClassLoader(),
                new Class<?>[]{UserProductSearchEventRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findPopularSearches" -> popularResults;
                    case "save" -> (UserProductSearchEvent) args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserProductSearchProperties properties() {
        return new UserProductSearchProperties(
                "v1",
                "v1",
                "v1",
                Duration.ofHours(24),
                Duration.ofMinutes(30),
                Duration.ofMinutes(30),
                100,
                Duration.ofSeconds(45),
                128,
                5,
                12,
                Duration.ofHours(24),
                Duration.ofDays(7),
                6,
                3,
                80);
    }

    private OpenRouterProperties openRouterProperties() {
        return new OpenRouterProperties(
                "https://openrouter.test/api/v1",
                "test-key",
                "Meant",
                new OpenRouterProperties.Models(
                        "preference-model",
                        "openrouter/free",
                        "explainer-model",
                        "openrouter/free"
                )
        );
    }

    static class FakeOpenRouterChatClient extends OpenRouterChatClient {

        private String response;
        private boolean fail;
        private boolean called;
        private String model;

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
            called = true;
            this.model = model;
            if (fail) {
                throw new OpenRouterException("failed");
            }
            return response;
        }
    }
}
