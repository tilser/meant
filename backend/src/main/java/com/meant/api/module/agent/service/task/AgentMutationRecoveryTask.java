package com.meant.api.module.agent.service.task;

import com.meant.api.module.agent.service.AgentMutationRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentMutationRecoveryTask {

    private final AgentMutationRecoveryService recoveryService;

    // Startup maintenance includes mutation recovery before the application becomes ready.
    @Scheduled(
            fixedDelayString = "${commerce.agent.tool-deadline}",
            initialDelayString = "${commerce.agent.tool-deadline}"
    )
    public void recoverStaleExecutions() {
        recoveryService.recoverStaleExecutions();
    }
}
