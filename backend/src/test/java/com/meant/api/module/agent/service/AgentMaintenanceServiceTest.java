package com.meant.api.module.agent.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentRunEventRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class AgentMaintenanceServiceTest {

    @Test
    void startupRecoversOnlyExpiredLeasesAndDoesNotCancelValidRemoteWork() {
        Instant now = Instant.parse("2026-07-18T10:00:00Z");
        AgentRun expired = AgentRun.builder().id(UUID.randomUUID()).build();
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunService runService = mock(AgentRunService.class);
        AgentMutationRecoveryService mutationRecovery = mock(AgentMutationRecoveryService.class);
        AgentRunCoordinator coordinator = mock(AgentRunCoordinator.class);
        AgentProperties properties = mock(AgentProperties.class);
        when(properties.enabled()).thenReturn(true);
        when(runs.findExpiredLeases(eq(AgentRunStatus.RUNNING), eq(now), any(Pageable.class)))
                .thenReturn(List.of(expired));
        AgentMaintenanceService service = new AgentMaintenanceService(
                runs,
                mock(AgentRunEventRepository.class),
                runService,
                mutationRecovery,
                coordinator,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        service.recoverAfterRestart();

        verify(mutationRecovery).recoverStaleExecutions();
        verify(runService).expireLease(expired.getId());
        verify(runService, never()).cancel(any());
        verify(coordinator).scheduleQueuedRuns();
    }
}
