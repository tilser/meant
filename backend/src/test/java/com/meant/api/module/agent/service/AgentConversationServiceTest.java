package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.command.CreateAgentConversationCommand;
import com.meant.api.module.agent.service.command.DeleteAgentConversationCommand;
import com.meant.api.module.agent.service.query.GetAgentConversationQuery;
import com.meant.api.module.merchant.service.MerchantLookupService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentConversationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

    private final AgentConversationRepository conversationRepository = mock(AgentConversationRepository.class);
    private final AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
    private final AgentArtifactReferenceRepository artifactRepository =
            mock(AgentArtifactReferenceRepository.class);
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final MerchantLookupService merchantLookupService = mock(MerchantLookupService.class);
    private final AgentConversationService service = new AgentConversationService(
            conversationRepository,
            messageRepository,
            artifactRepository,
            runRepository,
            merchantLookupService,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void countsActiveConversationsForGuestLimits() {
        UUID userId = UUID.randomUUID();
        when(conversationRepository.countByUserIdAndStatus(userId, AgentConversationStatus.ACTIVE))
                .thenReturn(3L);

        assertThat(service.activeConversationCount(userId)).isEqualTo(3L);
    }

    @Test
    void validatesAndPersistsTheSelectedActiveMerchantScope() {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(new CreateAgentConversationCommand(userId, "Trail shoes", merchantId));

        verify(merchantLookupService).activeSearchResult(merchantId);
        assertThat(created.merchantId()).isEqualTo(merchantId);
    }

    @Test
    void exposesTheConversationQueueHeadWithoutReplacingTheLatestEventCursor() {
        UUID userId = UUID.randomUUID();
        AgentConversation conversation = AgentConversation.create(userId, "Trail shoes", NOW.minusSeconds(10));
        AgentRun running = AgentRun.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation.getId())
                .userId(userId)
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.RUNNING)
                .model("test-model")
                .promptVersion("test-v1")
                .lastEventCursor(3L)
                .createdAt(NOW.minusSeconds(8))
                .build();
        AgentRun latestQueued = AgentRun.builder()
                .id(UUID.randomUUID())
                .conversationId(conversation.getId())
                .userId(userId)
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.QUEUED)
                .model("test-model")
                .promptVersion("test-v1")
                .lastEventCursor(7L)
                .createdAt(NOW.minusSeconds(2))
                .build();
        when(conversationRepository.findByIdAndUserId(conversation.getId(), userId))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdAndSequenceNumberGreaterThanOrderBySequenceNumberAsc(
                eq(conversation.getId()),
                eq(0L),
                any()
        )).thenReturn(List.of());
        when(artifactRepository.findByConversationIdOrderByCreatedAtDescOrdinalAsc(
                eq(conversation.getId()),
                any()
        )).thenReturn(List.of());
        when(runRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId()))
                .thenReturn(Optional.of(latestQueued));
        when(runRepository.findFirstByConversationIdAndStatusInOrderByCreatedAtAscIdAsc(
                conversation.getId(),
                List.of(AgentRunStatus.RUNNING, AgentRunStatus.QUEUED)
        )).thenReturn(Optional.of(running));

        var result = service.get(new GetAgentConversationQuery(userId, conversation.getId(), 0L, 100));

        assertThat(result.currentRunId()).isEqualTo(running.getId());
        assertThat(result.latestCursor()).isEqualTo(7L);
    }

    @Test
    void permanentlyDeletesAnOwnedConversation() {
        UUID userId = UUID.randomUUID();
        AgentConversation conversation = AgentConversation.create(userId, "Sunglasses", NOW);
        when(conversationRepository.findOwnedForUpdate(conversation.getId(), userId))
                .thenReturn(Optional.of(conversation));

        service.delete(new DeleteAgentConversationCommand(userId, conversation.getId()));

        verify(conversationRepository).delete(conversation);
    }

    @Test
    void rejectsDeletionWhenTheConversationIsNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findOwnedForUpdate(conversationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(new DeleteAgentConversationCommand(userId, conversationId)))
                .isInstanceOf(AgentException.class)
                .hasMessage("The agent resource was not found.");

        verify(conversationRepository, never()).delete(any());
    }
}
