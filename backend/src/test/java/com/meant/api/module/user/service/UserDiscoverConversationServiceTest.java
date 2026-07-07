package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.module.user.repository.UserAssistantConversationRepository;
import com.meant.api.module.user.repository.UserAssistantMessageRepository;
import com.meant.api.module.user.service.command.DeleteUserDiscoverConversationCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SaveUserDiscoverConversationCommand;
import com.meant.api.module.user.service.dto.UserDiscoverConversationResult;
import com.meant.api.module.user.service.query.ListUserAssistantConversationsQuery;
import com.meant.api.module.user.service.query.ListUserDiscoverConversationsQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserDiscoverConversationServiceTest extends PostgresIntegrationTest {

    @Autowired
    private UserDiscoverConversationService userDiscoverConversationService;

    @Autowired
    private UserAssistantChatService userAssistantChatService;

    @Autowired
    private UserAssistantConversationRepository conversationRepository;

    @Autowired
    private UserAssistantMessageRepository messageRepository;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
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
                new SaveUserDiscoverConversationCommand(userId, conversationId, "running shoes", threadJson)
        );

        assertThat(saved.conversationId()).isEqualTo(conversationId);
        assertThat(saved.title()).isEqualTo("running shoes");
        assertThat(saved.threadJson()).isEqualTo(threadJson);

        String updatedThreadJson = """
                {"id":"%s","title":"SF hat","messages":[{"role":"you","text":"SF hat"}]}
                """.formatted(conversationId);
        userDiscoverConversationService.save(
                profileCommand(userId),
                new SaveUserDiscoverConversationCommand(userId, conversationId, "SF hat", updatedThreadJson)
        );

        List<UserDiscoverConversationResult> discoverConversations = userDiscoverConversationService.list(
                profileCommand(userId),
                new ListUserDiscoverConversationsQuery(userId, 50)
        );
        assertThat(discoverConversations).hasSize(1);
        assertThat(discoverConversations.getFirst().title()).isEqualTo("SF hat");
        assertThat(discoverConversations.getFirst().threadJson()).isEqualTo(updatedThreadJson);

        assertThat(userAssistantChatService.list(
                profileCommand(userId),
                new ListUserAssistantConversationsQuery(userId, 50)
        )).isEmpty();

        userDiscoverConversationService.delete(
                profileCommand(userId),
                new DeleteUserDiscoverConversationCommand(userId, conversationId)
        );

        assertThat(userDiscoverConversationService.list(
                profileCommand(userId),
                new ListUserDiscoverConversationsQuery(userId, 50)
        )).isEmpty();
        assertThat(conversationRepository.findById(conversationId)).isEmpty();
    }

    private EnsureUserProfileCommand profileCommand(UUID userId) {
        return new EnsureUserProfileCommand(userId, userId + "@example.com", "Mara", null);
    }
}
