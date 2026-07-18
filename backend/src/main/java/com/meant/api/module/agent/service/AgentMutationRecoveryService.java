package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.constant.AgentUserActionStatus;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentToolInvocationRepository;
import com.meant.api.module.agent.repository.AgentUserActionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentMutationRecoveryService {

    private static final Duration MINIMUM_RECOVERY_GRACE = Duration.ofSeconds(5);
    static final String STALE_CLASSIFICATION = "stale_execution";
    static final String STALE_MUTATION_MESSAGE =
            "The worker stopped before the mutation outcome could be confirmed.";
    private static final String STALE_READ_MESSAGE = "The worker stopped before the read completed.";

    private final AgentToolInvocationRepository invocationRepository;
    private final AgentUserActionRepository actionRepository;
    private final AgentProperties properties;
    private final Clock clock;

    @Transactional
    public void recoverStaleExecutions() {
        if (!properties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        Instant cutoff = staleCutoff(now, properties.toolDeadline());
        invocationRepository.markStaleMutationsUncertain(
                AgentToolInvocationStatus.RUNNING,
                AgentToolInvocationStatus.UNCERTAIN,
                AgentToolRisk.READ,
                STALE_CLASSIFICATION,
                STALE_MUTATION_MESSAGE,
                cutoff,
                now
        );
        invocationRepository.markStaleReadsFailed(
                AgentToolInvocationStatus.RUNNING,
                AgentToolInvocationStatus.FAILED,
                AgentToolRisk.READ,
                STALE_CLASSIFICATION,
                STALE_READ_MESSAGE,
                cutoff,
                now
        );
        actionRepository.markStaleRunningUncertain(
                AgentUserActionStatus.RUNNING,
                AgentUserActionStatus.UNCERTAIN,
                STALE_MUTATION_MESSAGE,
                cutoff,
                now
        );
    }

    static Instant staleCutoff(Instant now, Duration toolDeadline) {
        Duration grace = toolDeadline.compareTo(MINIMUM_RECOVERY_GRACE) >= 0
                ? toolDeadline
                : MINIMUM_RECOVERY_GRACE;
        return now.minus(toolDeadline).minus(grace);
    }
}
