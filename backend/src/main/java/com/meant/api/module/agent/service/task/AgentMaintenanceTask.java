package com.meant.api.module.agent.service.task;

import com.meant.api.module.agent.service.AgentMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentMaintenanceTask {

    private final AgentMaintenanceService maintenanceService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        maintenanceService.recoverAfterRestart();
    }

    @Scheduled(fixedDelayString = "${commerce.agent.stale-run-age}")
    public void recoverStaleRuns() {
        maintenanceService.recoverStaleRuns();
    }

    @Scheduled(fixedDelayString = "${commerce.agent.event-retention}")
    public void removeExpiredEvents() {
        maintenanceService.removeExpiredEvents();
    }
}
