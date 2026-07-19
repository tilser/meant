package com.meant.api.module.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.properties.AgentStreamAdmissionProperties;
import com.meant.api.module.agent.service.AgentRunEventNotifier;
import com.meant.api.module.agent.service.AgentRunService;
import com.meant.api.module.agent.service.dto.AgentRunEventResult;
import com.meant.api.module.agent.service.dto.AgentRunResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentEventStreamWriterTest {

    private AgentEventStreamWriter writer;

    @AfterEach
    void shutDownWriter() {
        if (writer != null) {
            writer.shutdown();
        }
    }

    @Test
    void afterCommitSignalWakesFollowerWithoutWaitingForTheFallbackPoll() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentRunService runService = mock(AgentRunService.class);
        when(runService.get(any())).thenReturn(run(runId, AgentRunStatus.RUNNING, 0L));
        AtomicInteger replayCount = new AtomicInteger();
        when(runService.replay(any())).thenAnswer(ignored -> replayCount.incrementAndGet() == 1
                ? List.of()
                : List.of(event(runId, 1L, "run.completed")));
        AgentRunEventNotifier notifier = new AgentRunEventNotifier();
        AgentEventStreamAdmission admission = admission();
        writer = new AgentEventStreamWriter(
                runService,
                properties(Duration.ofSeconds(10)),
                notifier,
                admission
        );

        writer.open(userId, runId, 0L);
        notifier.signalAfterCommit(runId, 1L);

        verify(runService, timeout(1_000).times(2)).replay(any());
        verify(runService, timeout(1_000).times(1)).get(any());
        awaitCleanup(admission, userId, runId);
    }

    @Test
    void slowFallbackReplaysEventsWrittenByAnotherApplicationInstance() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentRunService runService = mock(AgentRunService.class);
        when(runService.get(any())).thenReturn(run(runId, AgentRunStatus.RUNNING, 0L));
        AtomicInteger replayCount = new AtomicInteger();
        when(runService.replay(any())).thenAnswer(ignored -> replayCount.incrementAndGet() == 1
                ? List.of()
                : List.of(event(runId, 1L, "run.completed")));
        AgentEventStreamAdmission admission = admission();
        writer = new AgentEventStreamWriter(
                runService,
                properties(Duration.ofMillis(20)),
                new AgentRunEventNotifier(),
                admission
        );

        writer.open(userId, runId, 0L);

        verify(runService, timeout(1_000).times(2)).replay(any());
        awaitCleanup(admission, userId, runId);
    }

    @Test
    void initialReplayFailureReleasesAdmissionAndSubscription() {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentRunService runService = mock(AgentRunService.class);
        when(runService.get(any())).thenReturn(run(runId, AgentRunStatus.RUNNING, 0L));
        when(runService.replay(any())).thenThrow(new IllegalStateException("cursor expired"));
        AgentRunEventNotifier notifier = new AgentRunEventNotifier();
        AgentEventStreamAdmission admission = admission();
        writer = new AgentEventStreamWriter(
                runService,
                properties(Duration.ofSeconds(10)),
                notifier,
                admission
        );

        assertThatThrownBy(() -> writer.open(userId, runId, 0L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(admission.activeStreams(userId)).isZero();
    }

    private AgentProperties properties(Duration fallbackInterval) {
        AgentProperties properties = mock(AgentProperties.class);
        when(properties.eventStreamTimeout()).thenReturn(Duration.ofSeconds(5));
        when(properties.eventPollInterval()).thenReturn(fallbackInterval);
        when(properties.eventStreamQueueSize()).thenReturn(2);
        return properties;
    }

    private AgentEventStreamAdmission admission() {
        return new AgentEventStreamAdmission(new AgentStreamAdmissionProperties(4, 2));
    }

    private AgentRunResult run(UUID runId, AgentRunStatus status, long latestCursor) {
        return new AgentRunResult(
                runId,
                UUID.randomUUID(),
                status,
                "test-model",
                "test-v1",
                0,
                0,
                null,
                null,
                null,
                null,
                false,
                latestCursor,
                Instant.now(),
                Instant.now(),
                status.terminal() ? Instant.now() : null
        );
    }

    private AgentRunEventResult event(UUID runId, long cursor, String type) {
        return new AgentRunEventResult(
                1,
                cursor,
                UUID.randomUUID(),
                runId,
                type,
                Instant.now(),
                "{}"
        );
    }

    private void awaitCleanup(AgentEventStreamAdmission admission, UUID userId, UUID runId)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (admission.activeStreams(userId, runId) != 0 && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(admission.activeStreams(userId, runId)).isZero();
        assertThat(admission.trackedUsers()).isZero();
        assertThat(admission.trackedRuns()).isZero();
    }
}
