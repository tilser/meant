package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.command.CancelAgentRunCommand;
import com.meant.api.module.agent.service.command.SubmitAgentTurnCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentTurnServiceTest {

    @Test
    void cancelAndQueueLinksTheDurableUserMessageToItsNewRun() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID runningId = UUID.randomUUID();
        UUID queuedId = UUID.randomUUID();
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunService runService = mock(AgentRunService.class);
        Instant now = Instant.parse("2026-07-18T12:00:00Z");
        AgentConversation conversation = AgentConversation.builder()
                .id(conversationId)
                .userId(userId)
                .title("New conversation")
                .status(AgentConversationStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        AgentRun running = run(runningId, conversationId, userId, AgentRunStatus.RUNNING, now);
        AgentRun queued = run(queuedId, conversationId, userId, AgentRunStatus.QUEUED, now);
        when(conversations.findOwnedForUpdate(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(runs.findFirstByConversationIdAndStatusInOrderByCreatedAtAsc(
                conversationId, List.of(AgentRunStatus.RUNNING))).thenReturn(Optional.of(running));
        when(runs.findFirstByConversationIdAndStatusInOrderByCreatedAtAsc(
                conversationId, List.of(AgentRunStatus.QUEUED))).thenReturn(Optional.of(queued));
        when(messages.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(runs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AgentTurnService service = new AgentTurnService(
                conversations,
                messages,
                runs,
                runService,
                properties(),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        var accepted = service.submit(new SubmitAgentTurnCommand(
                userId,
                conversationId,
                "Find shoes like mine",
                "client-turn-1"
        ));

        verify(runService).requestCancellation(new CancelAgentRunCommand(userId, runningId));
        verify(runService).cancel(queuedId);
        assertThat(accepted.userMessage().runId()).isEqualTo(accepted.runId());
        assertThat(accepted.userMessage().sequenceNumber()).isEqualTo(1);
        assertThat(accepted.userMessage().correlationId()).isEqualTo("client-turn-1");
        assertThat(conversation.getTitle()).isEqualTo("Find shoes like mine");
    }

    @Test
    void retriesWithTheSameClientTurnIdReturnTheOriginalRunWithoutCancellingAnything() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-18T12:00:00Z");
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunService runService = mock(AgentRunService.class);
        AgentConversation conversation = AgentConversation.builder()
                .id(conversationId)
                .userId(userId)
                .title("Shoes")
                .status(AgentConversationStatus.ACTIVE)
                .lastSequenceNumber(1)
                .createdAt(now)
                .updatedAt(now)
                .build();
        AgentMessage message = AgentMessage.builder()
                .id(messageId)
                .conversationId(conversationId)
                .runId(runId)
                .role(com.meant.api.module.agent.constant.AgentMessageRole.USER)
                .contentKind(com.meant.api.module.agent.constant.AgentContentKind.TEXT)
                .sequenceNumber(1)
                .textContent("Find shoes like mine")
                .correlationId("client-turn-1")
                .createdAt(now)
                .build();
        AgentRun run = run(runId, conversationId, userId, AgentRunStatus.RUNNING, now);
        when(conversations.findOwnedForUpdate(conversationId, userId)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdAndRoleAndCorrelationId(
                conversationId,
                com.meant.api.module.agent.constant.AgentMessageRole.USER,
                "client-turn-1"
        )).thenReturn(Optional.of(message));
        when(runs.findByTriggeringMessageId(messageId)).thenReturn(Optional.of(run));
        AgentTurnService service = new AgentTurnService(
                conversations,
                messages,
                runs,
                runService,
                properties(),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        var accepted = service.submit(new SubmitAgentTurnCommand(
                userId,
                conversationId,
                "Find shoes like mine",
                "client-turn-1"
        ));

        assertThat(accepted.runId()).isEqualTo(runId);
        assertThat(accepted.userMessage().messageId()).isEqualTo(messageId);
        verify(runService, never()).requestCancellation(any());
        verify(messages, never()).saveAndFlush(any());
        verify(runs, never()).saveAndFlush(any());
    }

    private AgentRun run(
            UUID id,
            UUID conversationId,
            UUID userId,
            AgentRunStatus status,
            Instant now
    ) {
        return AgentRun.builder()
                .id(id)
                .conversationId(conversationId)
                .userId(userId)
                .triggeringMessageId(UUID.randomUUID())
                .status(status)
                .model("model")
                .promptVersion("v1")
                .createdAt(now)
                .build();
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 24000, 2, Duration.ofSeconds(30), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
