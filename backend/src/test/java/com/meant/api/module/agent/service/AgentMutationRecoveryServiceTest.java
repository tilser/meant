package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.constant.AgentUserActionStatus;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentToolInvocationRepository;
import com.meant.api.module.agent.repository.AgentUserActionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentMutationRecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Test
    void atomicallyTargetsOnlyExecutionsOlderThanDeadlineAndGrace() {
        AgentToolInvocationRepository invocationRepository = mock(AgentToolInvocationRepository.class);
        AgentUserActionRepository actionRepository = mock(AgentUserActionRepository.class);
        AgentMutationRecoveryService service = new AgentMutationRecoveryService(
                invocationRepository,
                actionRepository,
                properties(true, Duration.ofSeconds(30)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.recoverStaleExecutions();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(invocationRepository).markStaleMutationsUncertain(
                org.mockito.ArgumentMatchers.eq(AgentToolInvocationStatus.RUNNING),
                org.mockito.ArgumentMatchers.eq(AgentToolInvocationStatus.UNCERTAIN),
                org.mockito.ArgumentMatchers.eq(AgentToolRisk.READ),
                org.mockito.ArgumentMatchers.eq("stale_execution"),
                any(),
                cutoff.capture(),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
        assertThat(cutoff.getValue()).isEqualTo(NOW.minusSeconds(60));
        assertThat(NOW.minusSeconds(29)).isAfter(cutoff.getValue());
        verify(invocationRepository).markStaleReadsFailed(
                org.mockito.ArgumentMatchers.eq(AgentToolInvocationStatus.RUNNING),
                org.mockito.ArgumentMatchers.eq(AgentToolInvocationStatus.FAILED),
                org.mockito.ArgumentMatchers.eq(AgentToolRisk.READ),
                org.mockito.ArgumentMatchers.eq("stale_execution"),
                any(),
                org.mockito.ArgumentMatchers.eq(cutoff.getValue()),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
        verify(actionRepository).markStaleRunningUncertain(
                org.mockito.ArgumentMatchers.eq(AgentUserActionStatus.RUNNING),
                org.mockito.ArgumentMatchers.eq(AgentUserActionStatus.UNCERTAIN),
                any(),
                org.mockito.ArgumentMatchers.eq(cutoff.getValue()),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
    }

    @Test
    void disabledAgentLeavesFreshAndStaleRowsUntouched() {
        AgentToolInvocationRepository invocationRepository = mock(AgentToolInvocationRepository.class);
        AgentUserActionRepository actionRepository = mock(AgentUserActionRepository.class);
        AgentMutationRecoveryService service = new AgentMutationRecoveryService(
                invocationRepository,
                actionRepository,
                properties(false, Duration.ofSeconds(30)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        service.recoverStaleExecutions();

        verify(invocationRepository, never()).markStaleMutationsUncertain(
                any(), any(), any(), any(), any(), any(), any());
        verify(invocationRepository, never()).markStaleReadsFailed(
                any(), any(), any(), any(), any(), any(), any());
        verify(actionRepository, never()).markStaleRunningUncertain(any(), any(), any(), any(), any());
    }

    private AgentProperties properties(boolean enabled, Duration toolDeadline) {
        return new AgentProperties(
                enabled,
                "model",
                "fallback",
                "https://example.test",
                "key",
                "Meant",
                "https://example.test",
                "v1",
                "v1",
                0,
                1000,
                8,
                20,
                5,
                4,
                40,
                64000,
                24000,
                2,
                Duration.ofMinutes(2),
                Duration.ofSeconds(30),
                toolDeadline,
                Duration.ofSeconds(10),
                Duration.ofMillis(10),
                128,
                Duration.ofDays(1),
                Duration.ofMinutes(5)
        );
    }
}
