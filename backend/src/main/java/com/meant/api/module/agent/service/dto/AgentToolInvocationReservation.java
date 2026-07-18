package com.meant.api.module.agent.service.dto;

import java.util.List;
import java.util.UUID;

public record AgentToolInvocationReservation(
        UUID invocationId,
        boolean execute,
        String completedResultJson,
        List<AgentArtifactResult> completedArtifacts,
        boolean reconciliationRetry
) {

    public AgentToolInvocationReservation {
        completedArtifacts = completedArtifacts == null ? List.of() : List.copyOf(completedArtifacts);
    }
}
