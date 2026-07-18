package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentUserActionStatus;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.AgentUserAction;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentUserActionRepository;
import com.meant.api.module.agent.service.command.RecordAgentUserActionCommand;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentUserActionPersistenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");
    private static final String ARGUMENTS = "{\"canonicalProductKey\":\"product-1\"}";

    private AgentConversationRepository conversationRepository;
    private AgentMessageRepository messageRepository;
    private AgentUserActionRepository actionRepository;
    private AgentArtifactService artifactService;
    private AgentConversationMemoryService memoryService;
    private AgentJsonSupport jsonSupport;
    private AgentUserActionPersistenceService service;
    private AgentConversation conversation;
    private UUID userId;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(AgentConversationRepository.class);
        messageRepository = mock(AgentMessageRepository.class);
        actionRepository = mock(AgentUserActionRepository.class);
        artifactService = mock(AgentArtifactService.class);
        memoryService = mock(AgentConversationMemoryService.class);
        jsonSupport = mock(AgentJsonSupport.class);
        service = new AgentUserActionPersistenceService(
                conversationRepository,
                messageRepository,
                actionRepository,
                mock(AgentArtifactReferenceRepository.class),
                artifactService,
                memoryService,
                jsonSupport,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        userId = UUID.randomUUID();
        conversation = AgentConversation.create(userId, "Test", NOW);
        when(conversationRepository.findOwnedForUpdate(conversation.getId(), userId))
                .thenReturn(Optional.of(conversation));
    }

    @Test
    void uncertainActionRetryReusesTheOriginalActionId() {
        AgentUserAction action = uncertainAction();
        when(actionRepository.findByUserIdAndConversationIdAndIdempotencyKey(
                userId, conversation.getId(), "client-key"))
                .thenReturn(Optional.of(action));

        var reservation = service.reserve(command(ARGUMENTS), ARGUMENTS, "v1");

        assertThat(reservation.execute()).isTrue();
        assertThat(reservation.actionId()).isEqualTo(action.getId());
        assertThat(action.getStatus()).isEqualTo(AgentUserActionStatus.RESERVED);
        assertThat(action.getSafeMessage()).isNull();
        assertThat(action.getCompletedAt()).isNull();
    }

    @Test
    void clientIdempotencyKeyCannotBeReusedWithDifferentArguments() {
        AgentUserAction action = uncertainAction();
        when(actionRepository.findByUserIdAndConversationIdAndIdempotencyKey(
                userId, conversation.getId(), "client-key"))
                .thenReturn(Optional.of(action));
        String differentArguments = "{\"canonicalProductKey\":\"product-2\"}";

        assertThatThrownBy(() -> service.reserve(command(differentArguments), differentArguments, "v1"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("different tool contract or arguments");

        assertThat(action.getStatus()).isEqualTo(AgentUserActionStatus.UNCERTAIN);
    }

    @Test
    void uncertainActionCannotReplayAcrossToolContractVersions() {
        AgentUserAction action = uncertainAction();
        when(actionRepository.findByUserIdAndConversationIdAndIdempotencyKey(
                userId, conversation.getId(), "client-key"))
                .thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.reserve(command(ARGUMENTS), ARGUMENTS, "v2"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("different tool contract or arguments");

        assertThat(action.getStatus()).isEqualTo(AgentUserActionStatus.UNCERTAIN);
    }

    @Test
    void completedTranscriptUsesTheServerSafeSummaryInsteadOfClientSuppliedText() {
        AgentUserAction action = AgentUserAction.builder()
                .userId(userId)
                .conversationId(conversation.getId())
                .toolName("pin_product")
                .toolVersion("v1")
                .argumentsJson(ARGUMENTS)
                .idempotencyKey("client-key")
                .status(AgentUserActionStatus.RUNNING)
                .createdAt(NOW)
                .build();
        when(actionRepository.findById(action.getId())).thenReturn(Optional.of(action));
        when(messageRepository.save(org.mockito.ArgumentMatchers.any(AgentMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jsonSupport.bounded("{\"ok\":true}")).thenReturn("{\"ok\":true}");
        when(artifactService.persist(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(java.util.List.of());

        var result = service.complete(
                action.getId(),
                command(ARGUMENTS),
                AgentToolExecutionResult.read(
                        "{\"ok\":true}",
                        "Pinned the verified product.\u0000",
                        java.util.List.of()
                )
        );

        assertThat(result.message().textContent()).isEqualTo("Pinned the verified product.");
        assertThat(result.message().textContent()).doesNotContain("CLIENT CONTROLLED");
        verify(memoryService).refresh(conversation);
    }

    @Test
    void staleRunningActionIsRetryableButFreshRunningActionIsUntouched() {
        AgentUserAction stale = runningAction(NOW.minusSeconds(61));
        when(actionRepository.findByUserIdAndConversationIdAndIdempotencyKey(
                userId, conversation.getId(), "client-key"))
                .thenReturn(Optional.of(stale));

        var reservation = service.reserve(command(ARGUMENTS), ARGUMENTS, "v1");

        assertThat(reservation.actionId()).isEqualTo(stale.getId());
        assertThat(stale.getStatus()).isEqualTo(AgentUserActionStatus.RESERVED);

        AgentUserAction fresh = runningAction(NOW.minusSeconds(29));
        when(actionRepository.findByUserIdAndConversationIdAndIdempotencyKey(
                userId, conversation.getId(), "client-key"))
                .thenReturn(Optional.of(fresh));

        assertThatThrownBy(() -> service.reserve(command(ARGUMENTS), ARGUMENTS, "v1"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("already in progress");
        assertThat(fresh.getStatus()).isEqualTo(AgentUserActionStatus.RUNNING);
    }

    private AgentUserAction uncertainAction() {
        return AgentUserAction.builder()
                .userId(userId)
                .conversationId(conversation.getId())
                .toolName("pin_product")
                .toolVersion("v1")
                .argumentsJson(ARGUMENTS)
                .idempotencyKey("client-key")
                .status(AgentUserActionStatus.UNCERTAIN)
                .safeMessage("The action timed out")
                .createdAt(NOW)
                .completedAt(NOW)
                .build();
    }

    private AgentUserAction runningAction(Instant startedAt) {
        return AgentUserAction.builder()
                .userId(userId)
                .conversationId(conversation.getId())
                .toolName("pin_product")
                .toolVersion("v1")
                .argumentsJson(ARGUMENTS)
                .idempotencyKey("client-key")
                .status(AgentUserActionStatus.RUNNING)
                .createdAt(NOW.minusSeconds(120))
                .startedAt(startedAt)
                .build();
    }

    private RecordAgentUserActionCommand command(String arguments) {
        return new RecordAgentUserActionCommand(
                userId,
                conversation.getId(),
                "pin_product",
                arguments,
                "client-key",
                "CLIENT CONTROLLED SUMMARY"
        );
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true, "model", "fallback", "https://example.test", "key", "Meant",
                "https://example.test", "v1", "v1", 0, 1000, 8, 20, 5, 4, 40,
                64000, 24000, 2, Duration.ofMinutes(2), Duration.ofSeconds(30),
                Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofMillis(10), 128,
                Duration.ofDays(1), Duration.ofMinutes(5)
        );
    }
}
