package com.meant.api.module.agent.service.dto;

import java.util.UUID;

public record AgentUserActionReservation(
        UUID actionId,
        boolean execute,
        AgentUserActionResult completedResult,
        UUID merchantId,
        boolean reconciliationRetry
) {

    public AgentUserActionReservation(
            UUID actionId,
            boolean execute,
            AgentUserActionResult completedResult
    ) {
        this(actionId, execute, completedResult, null, false);
    }

    public AgentUserActionReservation(
            UUID actionId,
            boolean execute,
            AgentUserActionResult completedResult,
            UUID merchantId
    ) {
        this(actionId, execute, completedResult, merchantId, false);
    }
}
