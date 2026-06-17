package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterChatMessage;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.user.constant.UserAssistantMessageRole;
import com.meant.api.module.user.entity.UserAssistantConversation;
import com.meant.api.module.user.entity.UserAssistantMessage;
import com.meant.api.module.user.repository.UserAssistantConversationRepository;
import com.meant.api.module.user.repository.UserAssistantMessageRepository;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.command.SendUserAssistantMessageCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.UserAssistantConversationResult;
import com.meant.api.module.user.service.dto.UserAssistantPageContext;
import com.meant.api.module.user.service.dto.UserAssistantStreamEvent;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import com.meant.api.module.user.service.query.GetLatestUserAssistantConversationQuery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class UserAssistantChatServiceTest extends PostgresIntegrationTest {

    @Autowired
    private UserAssistantChatService userAssistantChatService;

    @Autowired
    private UserAssistantConversationRepository conversationRepository;

    @Autowired
    private UserAssistantMessageRepository messageRepository;

    @Autowired
    private FakeOpenRouterChatClient openRouterChatClient;

    @Autowired
    private FakeUserProductSearchService userProductSearchService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        openRouterChatClient.reset();
        userProductSearchService.reset();
    }

    @Test
    void streamPersistsConversationAndAssistantAnswer() {
        UUID userId = UUID.randomUUID();
        openRouterChatClient.routeResponse = """
                {"action":"answer","searchQuery":"","clarifyingQuestion":""}
                """;
        openRouterChatClient.streamChunks = List.of("Use your ", "saved list first.");

        List<UserAssistantStreamEvent> events = new ArrayList<>();
        userAssistantChatService.stream(upsertCommand(userId), command(userId, null, "What should I do?"), events::add);

        assertThat(events).extracting(UserAssistantStreamEvent::type)
                .containsExactly("metadata", "delta", "delta", "done");
        assertThat(events.getLast().text()).isEqualTo("Use your saved list first.");
        assertThat(openRouterChatClient.completeJsonModel).isEqualTo("openrouter/free");
        assertThat(openRouterChatClient.streamModel).isEqualTo("openrouter/free");

        UUID conversationId = events.getFirst().conversationId();
        List<UserAssistantMessage> messages = messageRepository
                .findTop50ByConversationIdAndUserIdOrderByCreatedAtDesc(conversationId, userId);
        Collections.reverse(messages);
        assertThat(messages).extracting(UserAssistantMessage::getRole)
                .containsExactly(UserAssistantMessageRole.USER, UserAssistantMessageRole.ASSISTANT);
        assertThat(messages.getLast().getContent()).isEqualTo("Use your saved list first.");
    }

    @Test
    void streamRunsProductSearchWhenRouteRequestsSearch() {
        UUID userId = UUID.randomUUID();
        openRouterChatClient.routeResponse = """
                {"action":"search_products","searchQuery":"organic cotton tee","clarifyingQuestion":""}
                """;
        openRouterChatClient.streamChunks = List.of("I found a strong tee.");
        UserProductSearchProductResult product = product();
        FakeUserProductSearchService.nextResult = new UserProductSearchResult(
                "organic cotton tee",
                "organic cotton tee",
                "profile",
                false,
                List.of(product)
        );

        List<UserAssistantStreamEvent> events = new ArrayList<>();
        userAssistantChatService.stream(upsertCommand(userId), command(userId, null, "Find me a tee"), events::add);

        assertThat(FakeUserProductSearchService.lastCommand.query()).isEqualTo("organic cotton tee");
        assertThat(events.getLast().products()).containsExactly(product);
    }

    @Test
    void streamParsesLooseRouteResponse() {
        UUID userId = UUID.randomUUID();
        openRouterChatClient.routeResponse = """
                * action: search_products
                * searchQuery: organic cotton tee
                * clarifyingQuestion:
                """;
        openRouterChatClient.streamChunks = List.of("I found a strong tee.");
        UserProductSearchProductResult product = product();
        FakeUserProductSearchService.nextResult = new UserProductSearchResult(
                "organic cotton tee",
                "organic cotton tee",
                "profile",
                false,
                List.of(product)
        );

        List<UserAssistantStreamEvent> events = new ArrayList<>();
        userAssistantChatService.stream(upsertCommand(userId), command(userId, null, "Find me a tee"), events::add);

        assertThat(FakeUserProductSearchService.lastCommand.query()).isEqualTo("organic cotton tee");
        assertThat(events.getLast().products()).containsExactly(product);
    }

    @Test
    void streamDoesNotSearchSavedContextQuestionWhenRouteResponseIsInvalid() {
        UUID userId = UUID.randomUUID();
        openRouterChatClient.routeResponse = """
                * I would compare the saved products.
                """;
        openRouterChatClient.streamChunks = List.of("From your saved list, start with the strongest match.");

        List<UserAssistantStreamEvent> events = new ArrayList<>();
        userAssistantChatService.stream(upsertCommand(userId), command(
                userId,
                null,
                "what is the best from products I have in saved?"
        ), events::add);

        assertThat(FakeUserProductSearchService.lastCommand).isNull();
        assertThat(events.getLast().text()).isEqualTo("From your saved list, start with the strongest match.");
    }

    @Test
    void latestRestoresPersistedMessagesWithProducts() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-17T10:00:00Z");
        UserAssistantConversation conversation = conversationRepository.save(
                UserAssistantConversation.create(userId, "Gift ideas", now));
        UserProductSearchProductResult product = product();
        messageRepository.save(UserAssistantMessage.create(
                conversation.getId(),
                userId,
                UserAssistantMessageRole.USER,
                "Find a gift",
                null,
                null,
                null,
                now
        ));
        messageRepository.save(UserAssistantMessage.create(
                conversation.getId(),
                userId,
                UserAssistantMessageRole.ASSISTANT,
                "I found one.",
                "openrouter/free",
                null,
                objectMapper.writeValueAsString(List.of(product)),
                now.plusSeconds(1)
        ));

        UserAssistantConversationResult result = userAssistantChatService.latest(
                upsertCommand(userId),
                new GetLatestUserAssistantConversationQuery(userId));

        assertThat(result.conversationId()).isEqualTo(conversation.getId());
        assertThat(result.messages()).extracting(message -> message.role().name())
                .containsExactly("USER", "ASSISTANT");
        assertThat(result.messages().getLast().products()).containsExactly(product);
    }

    private SendUserAssistantMessageCommand command(UUID userId, UUID conversationId, String message) {
        return new SendUserAssistantMessageCommand(
                userId,
                conversationId,
                message,
                new UserAssistantPageContext(
                        "discover",
                        "Your feed",
                        null,
                        null,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
    }

    private UpsertUserCommand upsertCommand(UUID userId) {
        return new UpsertUserCommand(userId, userId + "@example.com", "Mara", null);
    }

    private UserProductSearchProductResult product() {
        return new UserProductSearchProductResult(
                "product-key",
                "product-hash",
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "fieldloom.example",
                "Field Loom",
                null,
                1,
                0.8,
                0.9,
                "remote-product",
                "Heavyweight Organic Cotton Tee",
                null,
                "https://fieldloom.example/tee",
                null,
                3800L,
                3800L,
                "USD",
                true,
                null,
                "Organic cotton tee",
                null,
                null,
                null,
                null,
                "variant-1",
                "Medium",
                "38.00",
                "USD",
                null,
                null,
                true,
                1,
                0.95,
                1,
                94,
                "Organic cotton and no synthetic blend.",
                List.of("organic-cotton"),
                List.of()
        );
    }

    @TestConfiguration
    static class AssistantTestConfiguration {

        @Bean
        @Primary
        FakeOpenRouterChatClient testOpenRouterChatClient() {
            return new FakeOpenRouterChatClient();
        }

        @Bean
        @Primary
        FakeUserProductSearchService testUserProductSearchService() {
            return new FakeUserProductSearchService();
        }
    }

    static class FakeOpenRouterChatClient extends OpenRouterChatClient {

        private String routeResponse;
        private List<String> streamChunks = List.of();
        private String completeJsonModel;
        private String streamModel;

        FakeOpenRouterChatClient() {
            super(RestClient.builder(), new OpenRouterProperties(
                    "https://openrouter.test/api/v1",
                    "test-key",
                    "Meant",
                    new OpenRouterProperties.Models(
                            "preference-model",
                            "query-model",
                            "explainer-model",
                            "openrouter/free"
                    )));
        }

        void reset() {
            routeResponse = """
                    {"action":"answer","searchQuery":"","clarifyingQuestion":""}
                    """;
            streamChunks = List.of();
            completeJsonModel = null;
            streamModel = null;
        }

        @Override
        public String completeJson(
                String model,
                String systemPrompt,
                String userPrompt,
                String schemaName,
                OpenRouterJsonSchemaDefinition schema
        ) {
            completeJsonModel = model;
            return routeResponse;
        }

        @Override
        public void streamText(
                String model,
                List<OpenRouterChatMessage> messages,
                Consumer<String> chunkConsumer
        ) {
            streamModel = model;
            streamChunks.forEach(chunkConsumer);
        }
    }

    static class FakeUserProductSearchService extends UserProductSearchService {

        private static SearchUserProductsCommand lastCommand;
        private static UserProductSearchResult nextResult;

        FakeUserProductSearchService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        void reset() {
            lastCommand = null;
            nextResult = new UserProductSearchResult(
                    "empty",
                    "empty",
                    "profile",
                    false,
                    List.of()
            );
        }

        @Override
        public UserProductSearchResult search(
                UpsertUserCommand upsertCommand,
                SearchUserProductsCommand command
        ) {
            lastCommand = command;
            return nextResult;
        }
    }
}
