package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.user.entity.UserDiscoverConversation;
import com.meant.api.module.user.repository.UserDiscoverConversationRepository;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserDiscoverConversationTombstoneRepository;
import com.meant.api.module.user.service.command.DeleteUserDiscoverConversationCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SaveUserDiscoverConversationCommand;
import com.meant.api.module.user.service.dto.UserDiscoverConversationResult;
import com.meant.api.module.user.service.query.GetUserDiscoverConversationQuery;
import com.meant.api.module.user.service.query.ListUserDiscoverConversationsQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserDiscoverConversationServiceIT extends PostgresIntegrationTestSupport {

    @Autowired
    private UserDiscoverConversationService userDiscoverConversationService;

    @Autowired
    private UserDiscoverConversationRepository conversationRepository;

    @Autowired
    private UserDiscoverConversationTombstoneRepository tombstoneRepository;

    @Autowired
    private UserDiscoverConversationSnapshotSanitizer snapshotSanitizer;

    @BeforeEach
    void setUp() {
        conversationRepository.deleteAll();
        tombstoneRepository.deleteAll();
    }

    @Test
    void saveListAndDeleteDiscoverConversationSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String threadJson = """
                {"id":"%s","title":"running shoes","messages":[]}
                """.formatted(conversationId);

        UserDiscoverConversationResult saved = userDiscoverConversationService.save(
                profileCommand(userId),
                new SaveUserDiscoverConversationCommand(userId, conversationId, "running shoes", threadJson, null)
        );

        assertThat(saved.conversationId()).isEqualTo(conversationId);
        assertThat(saved.title()).isEqualTo("running shoes");
        assertThat(saved.threadJson()).isEqualTo(snapshotSanitizer.sanitize(threadJson));
        assertThat(saved.revision()).isZero();

        String updatedThreadJson = """
                {"id":"%s","title":"SF hat","messages":[{"role":"you","text":"SF hat"}]}
                """.formatted(conversationId);
        UserDiscoverConversationResult updated = userDiscoverConversationService.save(
                profileCommand(userId),
                new SaveUserDiscoverConversationCommand(
                        userId, conversationId, "SF hat", updatedThreadJson, saved.revision())
        );
        assertThat(updated.revision()).isEqualTo(1);

        List<UserDiscoverConversationResult> discoverConversations = userDiscoverConversationService.list(
                profileCommand(userId),
                new ListUserDiscoverConversationsQuery(userId, 50)
        );
        assertThat(discoverConversations).hasSize(1);
        assertThat(discoverConversations.getFirst().title()).isEqualTo("SF hat");
        assertThat(discoverConversations.getFirst().threadJson())
                .isEqualTo(snapshotSanitizer.sanitize(updatedThreadJson));

        userDiscoverConversationService.delete(
                profileCommand(userId),
                new DeleteUserDiscoverConversationCommand(userId, conversationId)
        );

        assertThat(userDiscoverConversationService.list(
                profileCommand(userId),
                new ListUserDiscoverConversationsQuery(userId, 50)
        )).isEmpty();
        assertThat(conversationRepository.findById(conversationId)).isEmpty();
        assertThat(tombstoneRepository.existsById(conversationId)).isTrue();

        assertThatThrownBy(() -> userDiscoverConversationService.save(
                profileCommand(userId),
                new SaveUserDiscoverConversationCommand(
                        userId, conversationId, "stale title", threadJson, updated.revision())
        )).isInstanceOf(UserException.class)
                .hasMessageContaining("cannot be recreated");
    }

    @Test
    void getReturnsOnlyAnOwnedDiscoverConversation() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UserDiscoverConversationResult saved = userDiscoverConversationService.save(
                profileCommand(userId),
                new SaveUserDiscoverConversationCommand(
                        userId,
                        conversationId,
                        "owned",
                        "{\"messages\":[{\"role\":\"you\",\"text\":\"old chat\"}]}",
                        null));

        UserDiscoverConversationResult loaded = userDiscoverConversationService.get(
                new GetUserDiscoverConversationQuery(userId, conversationId));

        assertThat(loaded).isEqualTo(saved);
        assertThatThrownBy(() -> userDiscoverConversationService.get(
                new GetUserDiscoverConversationQuery(UUID.randomUUID(), conversationId)))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void saveAndGetRetainOnlyTheServerResultSetReferenceForHistoricalProducts() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID resultSetId = UUID.randomUUID();
        String threadJson = """
                {
                  "id":"%s",
                  "title":"shoes",
                  "messages":[{
                    "id":"assistant-result",
                    "role":"ai",
                    "blocks":[{
                      "type":"text",
                      "text":"I found two current options."
                    },{
                      "type":"products",
                      "productResultSetId":"%s",
                      "query":"shoes",
                      "products":[{"title":"private title","price":"$99"}],
                      "selectedProduct":{"image":"https://cdn.shopify.com/private.jpg"}
                    }]
                  }]
                }
                """.formatted(conversationId, resultSetId);

        UserDiscoverConversationResult saved = userDiscoverConversationService.save(
                profileCommand(userId),
                new SaveUserDiscoverConversationCommand(userId, conversationId, "shoes", threadJson, null)
        );
        UserDiscoverConversationResult loaded = userDiscoverConversationService.get(
                new GetUserDiscoverConversationQuery(userId, conversationId));

        assertThat(loaded.threadJson()).isEqualTo(saved.threadJson());
        assertThat(loaded.threadJson())
                .contains(
                        "I found two current options.",
                        "\"type\":\"products\"",
                        "\"productResultSetId\":\"" + resultSetId + "\"",
                        "\"query\":\"shoes\"")
                .doesNotContain("private title", "$99", "cdn.shopify.com", "selectedProduct");
    }

    @Test
    void savePreservesStructuredConversationBlocksAndKnownMessageContext() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String threadJson = """
                {
                  "id":"%s",
                  "products":[{"name":"top-level private product"}],
                  "messages":[{
                    "role":"ai",
                    "productContext":{"name":"private product"},
                    "blocks":[
                      {"type":"text","text":"Search completed."},
                      {"type":"products","products":[{"image":"https://cdn.shopify.com/private.jpg"}]}
                    ]
                  },{
                    "role":"ai",
                    "blocks":[{
                      "type":"prefs",
                      "preferences":[{"id":"natural","label":"Natural","desc":"Natural materials"}],
                      "products":[{"name":"nested private product"}]
                    }],
                    "sessionProducts":[{"name":"message private product"}]
                  }]
                }
                """.formatted(conversationId);

        UserDiscoverConversationResult saved = userDiscoverConversationService.save(
                profileCommand(userId),
                new SaveUserDiscoverConversationCommand(userId, conversationId, "products", threadJson, null)
        );

        assertThat(saved.threadJson())
                .contains(
                        "Search completed.",
                        "Natural materials",
                        "\"productContext\"",
                        "private product",
                        "\"type\":\"products\"",
                        "cdn.shopify.com",
                        "nested private product")
                .doesNotContain(
                        "top-level private product",
                        "message private product",
                        "sessionProducts");
    }

    @Test
    void listPreservesStoredLegacyConversationContext() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        userDiscoverConversationService.list(
                profileCommand(userId),
                new ListUserDiscoverConversationsQuery(userId, 50));
        String rawPayload = """
                {"messages":[{"id":"legacy","role":"ai","productContext":{"name":"legacy product"},
                "blocks":[{"type":"text","text":"Legacy price was $99"}]}]}
                """;
        conversationRepository.save(UserDiscoverConversation.create(
                conversationId, userId, "legacy", rawPayload, Instant.now()));

        UserDiscoverConversationResult listed = userDiscoverConversationService.list(
                        profileCommand(userId),
                        new ListUserDiscoverConversationsQuery(userId, 50))
                .getFirst();

        assertThat(listed.threadJson())
                .contains("Legacy price was $99", "legacy product", "productContext");
    }

    @Test
    void saveAndGetPreserveCompleteMultiTurnRichConversationUi() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID resultSetId = UUID.randomUUID();
        String threadJson = """
                {
                  "id":"%s",
                  "title":"running shoes",
                  "focusProductId":"canonical-shoe-1",
                  "messages":[{
                    "id":"search-question",
                    "role":"you",
                    "text":"Show me running shoes"
                  },{
                    "id":"search-answer",
                    "role":"ai",
                    "query":"running shoes",
                    "blocks":[{
                      "type":"text",
                      "text":"I found current matches."
                    },{
                      "type":"products",
                      "productResultSetId":"%s",
                      "query":"running shoes",
                      "products":[{"title":"private search title","price":12900}]
                    }]
                  },{
                    "id":"review-question",
                    "role":"you",
                    "text":"What do reviewers say?",
                    "productContext":{"title":"private search title"}
                  },{
                    "id":"review-answer",
                    "role":"ai",
                    "sessionOnly":true,
                    "blocks":[{
                      "type":"text",
                      "text":"Here is the review summary."
                    },{
                      "type":"reviews",
                      "product":{"title":"private search title","image":"https://cdn.shopify.com/private.jpg"}
                    },{
                      "type":"text",
                      "text":"The available evidence is limited."
                    }]
                  },{
                    "id":"compare-question",
                    "role":"you",
                    "text":"Compare these here."
                  },{
                    "id":"compare-answer",
                    "role":"ai",
                    "blocks":[{
                      "type":"text",
                      "text":"I lined them up here."
                    },{
                      "type":"minicompare",
                      "products":[{"title":"private comparison title","price":9900}],
                      "rows":[{"label":"Price","values":["$99"]}]
                    }]
                  },{
                    "id":"cart-answer",
                    "role":"ai",
                    "blocks":[{
                      "type":"text",
                      "text":"Here is your cart."
                    },{
                      "type":"cart",
                      "lines":[{"id":"canonical-shoe-1","merchant":"Running Store","qty":2}],
                      "products":[{"id":"canonical-shoe-1","title":"private cart title"}]
                    }]
                  }]
                }
                """.formatted(conversationId, resultSetId);

        UserDiscoverConversationResult saved = userDiscoverConversationService.save(
                profileCommand(userId),
                new SaveUserDiscoverConversationCommand(
                        userId, conversationId, "running shoes", threadJson, null));
        UserDiscoverConversationResult loaded = userDiscoverConversationService.get(
                new GetUserDiscoverConversationQuery(userId, conversationId));

        assertThat(loaded.threadJson()).isEqualTo(saved.threadJson());
        assertThat(loaded.threadJson())
                .containsSubsequence(
                        "Show me running shoes",
                        "I found current matches.",
                        "What do reviewers say?",
                        "Here is the review summary.",
                        "The available evidence is limited.",
                        "Compare these here.",
                        "I lined them up here.",
                        "Here is your cart.")
                .contains(
                        "\"focusProductId\":\"canonical-shoe-1\"",
                        "\"productResultSetId\":\"" + resultSetId + "\"",
                        "\"productContext\"",
                        "private search title",
                        "\"type\":\"reviews\"",
                        "cdn.shopify.com",
                        "\"type\":\"minicompare\"",
                        "private comparison title",
                        "9900",
                        "$99",
                        "\"type\":\"cart\"",
                        "private cart title",
                        "\"qty\":2")
                .doesNotContain(
                        "12900",
                        "sessionOnly");
        assertThat(snapshotSanitizer.sanitize(loaded.threadJson())).isEqualTo(loaded.threadJson());
    }

    @Test
    void concurrentFirstSavesSerializeToOneCreateAndOneConflict() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        userDiscoverConversationService.list(
                profileCommand(userId),
                new ListUserDiscoverConversationsQuery(userId, 50));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var attempts = List.of("first", "second").stream()
                    .map(title -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            return userDiscoverConversationService.save(
                                    profileCommand(userId),
                                    new SaveUserDiscoverConversationCommand(
                                            userId,
                                            conversationId,
                                            title,
                                            "{\"messages\":[]}",
                                            null));
                        } catch (RuntimeException exception) {
                            return exception;
                        }
                    }))
                    .toList();
            ready.await();
            start.countDown();
            List<Object> outcomes = attempts.stream().map(attempt -> {
                try {
                    return attempt.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();

            assertThat(outcomes).filteredOn(UserDiscoverConversationResult.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(UserException.class::isInstance).hasSize(1);
            assertThat(outcomes.stream()
                    .filter(UserException.class::isInstance)
                    .map(UserException.class::cast)
                    .findFirst()
                    .orElseThrow())
                    .hasMessageContaining("changed before it could be saved");
        } finally {
            executor.shutdownNow();
        }
    }

    private EnsureUserProfileCommand profileCommand(UUID userId) {
        return new EnsureUserProfileCommand(userId, userId + "@example.com", "Mara", null);
    }
}
