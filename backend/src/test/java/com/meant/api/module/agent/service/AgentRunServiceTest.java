package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.entity.AgentRunEvent;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentRunEventRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Test
    void claimsAQueuedRunWithThisProcessIdentityAndAFiniteLease() {
        UUID runId = UUID.randomUUID();
        AgentRun run = queued(runId);
        Fixture fixture = fixture(run);

        var claim = fixture.service().claim(runId);

        assertThat(claim).isPresent();
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(run.getExecutionOwner()).isEqualTo(claim.orElseThrow());
        assertThat(run.getHeartbeatAt()).isEqualTo(NOW);
        assertThat(run.getLeaseExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3)));
        ArgumentCaptor<AgentRunEvent> event = ArgumentCaptor.forClass(AgentRunEvent.class);
        verify(fixture.events()).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(AgentRunEventType.RUN_STARTED);
        verify(fixture.eventNotifier()).signalAfterCommit(runId, 1L);
    }

    @Test
    void leavesTheRunQueuedWhenItsConversationIsAlreadyClaimed() {
        UUID runId = UUID.randomUUID();
        AgentRun run = queued(runId);
        Fixture fixture = fixture(run);
        when(fixture.runs().existsByConversationIdAndStatusAndIdNot(
                run.getConversationId(), AgentRunStatus.RUNNING, runId)).thenReturn(true);

        var claim = fixture.service().claim(runId);

        assertThat(claim).isEmpty();
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(run.getExecutionOwner()).isNull();
    }

    @Test
    void anExpiredLeaseCannotBeRenewedByItsFormerOwner() {
        UUID runId = UUID.randomUUID();
        UUID executionOwner = UUID.randomUUID();
        AgentRun run = AgentRun.builder()
                .id(runId)
                .conversationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.RUNNING)
                .model("test-model")
                .promptVersion("test-v1")
                .createdAt(NOW.minusSeconds(30))
                .startedAt(NOW.minusSeconds(30))
                .executionOwner(executionOwner)
                .heartbeatAt(NOW.minusSeconds(30))
                .leaseExpiresAt(NOW)
                .build();
        Fixture fixture = fixture(run);

        assertThat(fixture.service().renewLease(runId, executionOwner)).isFalse();
        assertThat(fixture.service().cancellationRequested(runId, executionOwner)).isTrue();
        assertThat(run.getLeaseExpiresAt()).isEqualTo(NOW);
    }

    @Test
    void expiringAnAbandonedRunFencesItAndRequeuesTheSameRunForSafeReplay() {
        UUID runId = UUID.randomUUID();
        AgentRun run = AgentRun.builder()
                .id(runId)
                .conversationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.RUNNING)
                .model("test-model")
                .promptVersion("test-v1")
                .createdAt(NOW.minusSeconds(30))
                .startedAt(NOW.minusSeconds(30))
                .executionOwner(UUID.randomUUID())
                .heartbeatAt(NOW.minusSeconds(30))
                .leaseExpiresAt(NOW.minusSeconds(1))
                .build();
        Fixture fixture = fixture(run);

        assertThat(fixture.service().expireLease(runId)).isTrue();

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.QUEUED);
        assertThat(run.getId()).isEqualTo(runId);
        assertThat(run.getFailureCode()).isNull();
        assertThat(run.getExecutionOwner()).isNull();
        assertThat(run.getLeaseExpiresAt()).isNull();
        assertThat(run.getHeartbeatAt()).isNull();
    }

    @Test
    void reclaimingARecoveredRunEmitsARestartEventWithoutChangingItsIdentity() {
        UUID runId = UUID.randomUUID();
        AgentRun run = AgentRun.builder()
                .id(runId)
                .conversationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.QUEUED)
                .model("test-model")
                .promptVersion("test-v1")
                .createdAt(NOW.minusSeconds(30))
                .startedAt(NOW.minusSeconds(20))
                .build();
        Fixture fixture = fixture(run);

        assertThat(fixture.service().claim(runId)).isPresent();

        ArgumentCaptor<AgentRunEvent> event = ArgumentCaptor.forClass(AgentRunEvent.class);
        verify(fixture.events()).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(AgentRunEventType.RUN_STARTED);
        verify(fixture.json()).write(org.mockito.ArgumentMatchers.argThat(payload ->
                payload.toString().contains("restarted after worker recovery")));
        assertThat(run.getId()).isEqualTo(runId);
    }

    @Test
    void anOldWorkerCannotCancelAReclaimedExecutionOfTheSameRun() {
        UUID runId = UUID.randomUUID();
        UUID abandonedOwner = UUID.randomUUID();
        AgentRun run = AgentRun.builder()
                .id(runId)
                .conversationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.RUNNING)
                .model("test-model")
                .promptVersion("test-v1")
                .createdAt(NOW.minusSeconds(30))
                .startedAt(NOW.minusSeconds(30))
                .executionOwner(abandonedOwner)
                .heartbeatAt(NOW.minusSeconds(30))
                .leaseExpiresAt(NOW.minusSeconds(1))
                .build();
        Fixture fixture = fixture(run);

        assertThat(fixture.service().expireLease(runId)).isTrue();
        UUID replacementOwner = fixture.service().claim(runId).orElseThrow();

        assertThat(fixture.service().cancelOwnedExecution(runId, abandonedOwner)).isFalse();
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(run.getExecutionOwner()).isEqualTo(replacementOwner);
    }

    @Test
    void anExpiredRunWithAQueuedReplacementRequestRemainsCancelled() {
        UUID runId = UUID.randomUUID();
        AgentRun run = AgentRun.builder()
                .id(runId)
                .conversationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.RUNNING)
                .model("test-model")
                .promptVersion("test-v1")
                .createdAt(NOW.minusSeconds(30))
                .startedAt(NOW.minusSeconds(30))
                .executionOwner(UUID.randomUUID())
                .heartbeatAt(NOW.minusSeconds(30))
                .leaseExpiresAt(NOW.minusSeconds(1))
                .build();
        run.requestCancellation();
        Fixture fixture = fixture(run);

        assertThat(fixture.service().expireLease(runId)).isTrue();

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(run.getExecutionOwner()).isNull();
    }

    private Fixture fixture(AgentRun run) {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        AgentRunEventNotifier eventNotifier = mock(AgentRunEventNotifier.class);
        when(runs.findForUpdate(run.getId())).thenReturn(Optional.of(run));
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(json.write(any())).thenReturn("{}");
        AgentRunService service = new AgentRunService(
                runs,
                events,
                json,
                mock(AgentMetrics.class),
                eventNotifier,
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(service, runs, events, json, eventNotifier);
    }

    private AgentRun queued(UUID id) {
        return AgentRun.builder()
                .id(id)
                .conversationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.QUEUED)
                .model("test-model")
                .promptVersion("test-v1")
                .createdAt(NOW.minusSeconds(1))
                .build();
    }

    private AgentProperties properties() {
        return new AgentProperties(
                true,
                "test-model",
                "fallback-model",
                "https://example.test/v1",
                "test-key",
                "Meant Test",
                "https://example.test",
                "test-v1",
                "test-v1",
                0,
                1024,
                8,
                20,
                6,
                4,
                40,
                64000,
                24000,
                2,
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofMillis(10),
                128,
                Duration.ofDays(1),
                Duration.ofMinutes(3)
        );
    }

    private record Fixture(
            AgentRunService service,
            AgentRunRepository runs,
            AgentRunEventRepository events,
            AgentJsonSupport json,
            AgentRunEventNotifier eventNotifier
    ) {
    }
}
