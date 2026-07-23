package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentRunEventRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class AgentMaintenanceService {

    private static final int STALE_RUN_PAGE_SIZE = 100;

    private final AgentRunRepository runRepository;
    private final AgentRunEventRepository eventRepository;
    private final AgentRunService runService;
    private final AgentMutationRecoveryService mutationRecoveryService;
    private final AgentRunCoordinator coordinator;
    private final AgentProperties properties;
    private final Clock clock;

    public void recoverStaleRuns() {
        if (!properties.enabled()) {
            return;
        }
        mutationRecoveryService.recoverStaleExecutions();
        recoverExpiredLeases();
        coordinator.scheduleQueuedRuns();
    }

    public void recoverAfterRestart() {
        recoverStaleRuns();
    }

    private void recoverExpiredLeases() {
        Instant now = clock.instant();
        List<AgentRun> expired;
        do {
            expired = runRepository.findExpiredLeases(
                    AgentRunStatus.RUNNING,
                    now,
                    PageRequest.of(0, STALE_RUN_PAGE_SIZE)
            );
            expired.stream().map(AgentRun::getId).forEach(runService::expireLease);
        } while (expired.size() == STALE_RUN_PAGE_SIZE);
    }

    @Transactional
    public long removeExpiredEvents() {
        return eventRepository.deleteByOccurredAtBefore(clock.instant().minus(properties.eventRetention()));
    }
}
