package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.entity.AgentToolInvocation;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.repository.AgentToolInvocationRepository;
import com.meant.api.module.agent.service.AgentArtifactService;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentMessageLedgerService;
import com.meant.api.module.agent.service.AgentMetrics;
import com.meant.api.module.agent.service.AgentRunService;
import com.meant.api.module.agent.service.dto.AgentMessageResult;
import com.meant.api.module.agent.service.dto.AgentModelToolCall;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentToolInvocationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");
    private static final String ARGUMENTS = "{\"offerKey\":\"offer-1\"}";

    private AgentToolInvocationRepository invocationRepository;
    private AgentArtifactReferenceRepository artifactRepository;
    private AgentRunRepository runRepository;
    private AgentRunService runService;
    private AgentMessageLedgerService messageLedgerService;
    private AgentArtifactService artifactService;
    private AgentJsonSupport jsonSupport;
    private AgentToolInvocationService service;
    private AgentRun run;

    @BeforeEach
    void setUp() {
        invocationRepository = mock(AgentToolInvocationRepository.class);
        artifactRepository = mock(AgentArtifactReferenceRepository.class);
        runRepository = mock(AgentRunRepository.class);
        runService = mock(AgentRunService.class);
        messageLedgerService = mock(AgentMessageLedgerService.class);
        artifactService = mock(AgentArtifactService.class);
        jsonSupport = mock(AgentJsonSupport.class);
        service = new AgentToolInvocationService(
                invocationRepository,
                artifactRepository,
                runRepository,
                runService,
                messageLedgerService,
                artifactService,
                jsonSupport,
                mock(AgentMetrics.class),
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        UUID runId = UUID.randomUUID();
        run = AgentRun.builder()
                .id(runId)
                .conversationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.RUNNING)
                .model("test")
                .promptVersion("v1")
                .createdAt(NOW)
                .build();
        when(runRepository.findForUpdate(runId)).thenReturn(Optional.of(run));
    }

    @Test
    void uncertainMutationRetryReusesTheOriginalInvocationAndProviderIdempotencyIdentity() {
        AgentToolInvocation invocation = uncertainInvocation(run.getId(), ARGUMENTS);
        when(invocationRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .thenReturn(Optional.of(invocation));
        when(jsonSupport.bounded(ARGUMENTS)).thenReturn(ARGUMENTS);

        var reservation = service.reserve(
                run.getId(),
                new AgentModelToolCall("retry-call", "prepare_carts", ARGUMENTS),
                descriptor(),
                ARGUMENTS,
                "idem-1"
        );

        assertThat(reservation.execute()).isTrue();
        assertThat(reservation.invocationId()).isEqualTo(invocation.getId());
        assertThat(invocation.getStatus()).isEqualTo(AgentToolInvocationStatus.PROPOSED);
        assertThat(run.getToolInvocationCount()).isEqualTo(1);
        verify(runService).append(
                org.mockito.ArgumentMatchers.eq(run.getId()),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void retryRejectsAnyFingerprintMismatchBeforeReexecutingCommerce() {
        AgentToolInvocation invocation = uncertainInvocation(run.getId(), ARGUMENTS);
        when(invocationRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .thenReturn(Optional.of(invocation));
        String differentArguments = "{\"offerKey\":\"offer-2\"}";
        when(jsonSupport.bounded(differentArguments)).thenReturn(differentArguments);

        assertThatThrownBy(() -> service.reserve(
                run.getId(),
                new AgentModelToolCall("retry-call", "prepare_carts", differentArguments),
                descriptor(),
                differentArguments,
                "idem-1"
        )).isInstanceOf(AgentException.class)
                .hasMessageContaining("different tool contract or arguments");

        assertThat(invocation.getStatus()).isEqualTo(AgentToolInvocationStatus.UNCERTAIN);
        assertThat(run.getToolInvocationCount()).isZero();
    }

    @Test
    void staleRunningMutationIsLazilyRecoveredAndRetriedWithTheSameInvocationId() {
        AgentToolInvocation invocation = runningInvocation(NOW.minusSeconds(61));
        when(invocationRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .thenReturn(Optional.of(invocation));
        when(jsonSupport.bounded(ARGUMENTS)).thenReturn(ARGUMENTS);

        var reservation = service.reserve(
                run.getId(),
                new AgentModelToolCall("retry-call", "prepare_carts", ARGUMENTS),
                descriptor(),
                ARGUMENTS,
                "idem-1"
        );

        assertThat(reservation.invocationId()).isEqualTo(invocation.getId());
        assertThat(reservation.reconciliationRetry()).isTrue();
        assertThat(invocation.getStatus()).isEqualTo(AgentToolInvocationStatus.PROPOSED);
    }

    @Test
    void freshRunningMutationRemainsInProgressAndCannotBeReplayed() {
        AgentToolInvocation invocation = runningInvocation(NOW.minusSeconds(29));
        when(invocationRepository.findByIdempotencyKeyForUpdate("idem-1"))
                .thenReturn(Optional.of(invocation));
        when(jsonSupport.bounded(ARGUMENTS)).thenReturn(ARGUMENTS);

        assertThatThrownBy(() -> service.reserve(
                run.getId(),
                new AgentModelToolCall("retry-call", "prepare_carts", ARGUMENTS),
                descriptor(),
                ARGUMENTS,
                "idem-1"
        )).isInstanceOf(AgentException.class)
                .hasMessageContaining("already in progress");

        assertThat(invocation.getStatus()).isEqualTo(AgentToolInvocationStatus.RUNNING);
    }

    @Test
    void completionAcquiresTheConversationAndRunLedgerLocksBeforeMutatingTheInvocation() {
        UUID invocationId = UUID.randomUUID();
        UUID executionOwner = UUID.randomUUID();
        AgentToolInvocation invocation = runningInvocation(NOW.minusSeconds(1));
        when(invocationRepository.findById(invocationId)).thenReturn(Optional.of(invocation));
        when(jsonSupport.bounded("{\"ok\":true}")).thenReturn("{\"ok\":true}");
        when(messageLedgerService.appendToolResult(
                run.getId(), executionOwner, "call-1", "prepare_carts", "{\"ok\":true}"
        )).thenReturn(new AgentMessageResult(
                UUID.randomUUID(), run.getId(), 1, null, null, null, "{\"ok\":true}", null, NOW
        ));

        service.complete(
                run.getId(),
                run.getConversationId(),
                invocationId,
                "call-1",
                "prepare_carts",
                AgentToolExecutionResult.read("{\"ok\":true}", "Prepared cart", java.util.List.of()),
                10,
                executionOwner
        );

        var ordered = inOrder(messageLedgerService, invocationRepository, artifactService);
        ordered.verify(messageLedgerService).appendToolResult(
                run.getId(), executionOwner, "call-1", "prepare_carts", "{\"ok\":true}"
        );
        ordered.verify(invocationRepository).findById(invocationId);
        ordered.verify(artifactService).persist(
                org.mockito.ArgumentMatchers.eq(run.getConversationId()),
                org.mockito.ArgumentMatchers.eq(run.getId()),
                org.mockito.ArgumentMatchers.eq(executionOwner),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(invocationId),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    private AgentToolInvocation uncertainInvocation(UUID runId, String arguments) {
        return AgentToolInvocation.builder()
                .runId(runId)
                .modelToolCallId("initial-call")
                .toolName("prepare_carts")
                .toolVersion("v1")
                .riskClass(AgentToolRisk.REVERSIBLE_MUTATION)
                .status(AgentToolInvocationStatus.UNCERTAIN)
                .argumentsJson(arguments)
                .idempotencyKey("idem-1")
                .createdAt(NOW)
                .startedAt(NOW)
                .completedAt(NOW)
                .failureClassification("timeout")
                .safeMessage("Timed out")
                .build();
    }

    private AgentToolInvocation runningInvocation(Instant startedAt) {
        return AgentToolInvocation.builder()
                .runId(run.getId())
                .modelToolCallId("initial-call")
                .toolName("prepare_carts")
                .toolVersion("v1")
                .riskClass(AgentToolRisk.REVERSIBLE_MUTATION)
                .status(AgentToolInvocationStatus.RUNNING)
                .argumentsJson(ARGUMENTS)
                .idempotencyKey("idem-1")
                .createdAt(NOW.minusSeconds(120))
                .startedAt(startedAt)
                .build();
    }

    private AgentToolDescriptor descriptor() {
        return new AgentToolDescriptor(
                "prepare_carts",
                "Prepare carts",
                "{\"type\":\"object\"}",
                "v1",
                AgentToolRisk.REVERSIBLE_MUTATION
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
