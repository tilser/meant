package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.entity.AgentRunEvent;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentRunEventRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.dto.AgentArtifactResult;
import com.meant.api.module.agent.service.dto.AgentEventPayload;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

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
    void claimsAQueuedRunWhenTheSameUserIsRunningAnotherConversation() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID otherConversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentRun run = queued(runId, conversationId, userId);
        AgentRun otherConversationRun = AgentRun.builder()
                .id(UUID.randomUUID())
                .conversationId(otherConversationId)
                .userId(userId)
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.RUNNING)
                .model("test-model")
                .promptVersion("test-v1")
                .createdAt(NOW.minusSeconds(2))
                .startedAt(NOW.minusSeconds(1))
                .executionOwner(UUID.randomUUID())
                .heartbeatAt(NOW.minusSeconds(1))
                .leaseExpiresAt(NOW.plusSeconds(30))
                .build();
        Fixture fixture = fixture(run);
        when(fixture.runs().existsByConversationIdAndStatusAndIdNot(any(), any(), any()))
                .thenAnswer(invocation -> {
                    UUID requestedConversationId = invocation.getArgument(0);
                    AgentRunStatus requestedStatus = invocation.getArgument(1);
                    UUID excludedRunId = invocation.getArgument(2);
                    return otherConversationRun.getConversationId().equals(requestedConversationId)
                            && otherConversationRun.getStatus() == requestedStatus
                            && !otherConversationRun.getId().equals(excludedRunId);
                });

        var claim = fixture.service().claim(runId);

        assertThat(otherConversationRun.getUserId()).isEqualTo(run.getUserId());
        assertThat(otherConversationRun.getConversationId()).isNotEqualTo(run.getConversationId());
        assertThat(otherConversationRun.getStatus()).isEqualTo(AgentRunStatus.RUNNING);
        assertThat(claim).isPresent();
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.RUNNING);
        verify(fixture.runs()).existsByConversationIdAndStatusAndIdNot(
                conversationId, AgentRunStatus.RUNNING, runId);
    }

    @Test
    void leavesTheRunQueuedWhenItsConversationAlreadyHasAClaimedRun() {
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
        verify(fixture.json()).writeArtifact(org.mockito.ArgumentMatchers.argThat(payload ->
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

    @Test
    void oversizedArtifactEventRetainsItsDeclaredEnvelope() throws Exception {
        UUID runId = UUID.randomUUID();
        AgentRun run = queued(runId);
        ObjectMapper objectMapper = new ObjectMapper();
        Fixture fixture = fixture(
                run,
                new AgentJsonSupport(objectMapper, properties(256))
        );
        UUID executionOwner = fixture.service().claim(runId).orElseThrow();
        String artifactPayload = "{\"description\":\"" + "event".repeat(500) + "\"}";
        AgentArtifactResult artifact = new AgentArtifactResult(
                UUID.randomUUID(), UUID.randomUUID(), runId, AgentArtifactType.PRODUCT,
                1, "product:large", "Large product", "product:large", null,
                null, null, null, null, artifactPayload, NOW
        );

        fixture.service().append(
                runId,
                executionOwner,
                AgentRunEventType.ARTIFACT_UPSERTED,
                AgentEventPayload.artifact(artifact)
        );

        ArgumentCaptor<AgentRunEvent> events = ArgumentCaptor.forClass(AgentRunEvent.class);
        verify(fixture.events(), times(2)).save(events.capture());
        String payloadJson = events.getAllValues().getLast().getPayloadJson();
        assertThat(payloadJson).hasSizeGreaterThan(256);
        assertThat(objectMapper.readTree(payloadJson).at("/artifact/type").asText())
                .isEqualTo("PRODUCT");
        assertThat(objectMapper.readTree(payloadJson).at("/artifact/payloadJson").asText())
                .isEqualTo(artifactPayload);
        assertThat(objectMapper.readTree(payloadJson).has("truncated")).isFalse();
    }

    @Test
    void appendsAnOwnedEventBatchWithOneRunLockAndOrderedCursorsAndNotifications() {
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
                .heartbeatAt(NOW)
                .leaseExpiresAt(NOW.plusSeconds(30))
                .build();
        Fixture fixture = fixture(run);

        var results = fixture.service().appendAll(
                runId,
                executionOwner,
                AgentRunEventType.ARTIFACT_UPSERTED,
                List.of(
                        AgentEventPayload.text("first"),
                        AgentEventPayload.text("second"),
                        AgentEventPayload.text("third")
                )
        );

        verify(fixture.runs(), times(1)).findForUpdate(runId);
        ArgumentCaptor<AgentRunEvent> events = ArgumentCaptor.forClass(AgentRunEvent.class);
        verify(fixture.events(), times(3)).save(events.capture());
        assertThat(events.getAllValues())
                .extracting(AgentRunEvent::getCursor)
                .containsExactly(1L, 2L, 3L);
        assertThat(results)
                .extracting(result -> result.cursor())
                .containsExactly(1L, 2L, 3L);
        verify(fixture.eventNotifier()).signalAfterCommit(runId, 1L);
        verify(fixture.eventNotifier()).signalAfterCommit(runId, 2L);
        verify(fixture.eventNotifier()).signalAfterCommit(runId, 3L);
    }

    private Fixture fixture(AgentRun run) {
        AgentJsonSupport json = mock(AgentJsonSupport.class);
        when(json.writeArtifact(any())).thenReturn("{}");
        return fixture(run, json);
    }

    private Fixture fixture(AgentRun run, AgentJsonSupport json) {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        AgentRunEventNotifier eventNotifier = mock(AgentRunEventNotifier.class);
        when(runs.findForUpdate(run.getId())).thenReturn(Optional.of(run));
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
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
        return queued(id, UUID.randomUUID(), UUID.randomUUID());
    }

    private AgentRun queued(UUID id, UUID conversationId, UUID userId) {
        return AgentRun.builder()
                .id(id)
                .conversationId(conversationId)
                .userId(userId)
                .triggeringMessageId(UUID.randomUUID())
                .status(AgentRunStatus.QUEUED)
                .model("test-model")
                .promptVersion("test-v1")
                .createdAt(NOW.minusSeconds(1))
                .build();
    }

    private AgentProperties properties() {
        return properties(24000);
    }

    private AgentProperties properties(int maximumResultCharacters) {
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
                maximumResultCharacters,
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
