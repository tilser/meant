package com.meant.api.module.agent.service.dto;

import java.util.UUID;

public record AgentUserActionReservation(
        UUID actionId,
        boolean execute,
        AgentUserActionResult completedResult
) {
}
