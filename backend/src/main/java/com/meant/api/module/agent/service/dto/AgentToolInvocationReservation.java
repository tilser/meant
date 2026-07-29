package com.meant.api.module.agent.service.dto;

import java.util.List;
import java.util.UUID;

public record AgentToolInvocationReservation(
        UUID invocationId,
        boolean execute,
        String completedResultJson,
        List<AgentArtifactResult> completedArtifacts,
        boolean reconciliationRetry,
        String waitingForUserMessage
) {

    public AgentToolInvocationReservation {
        completedArtifacts = completedArtifacts == null ? List.of() : List.copyOf(completedArtifacts);
        waitingForUserMessage = waitingForUserMessage == null || waitingForUserMessage.isBlank()
                ? null
                : waitingForUserMessage.trim();
    }

    public AgentToolInvocationReservation(
            UUID invocationId,
            boolean execute,
            String completedResultJson,
            List<AgentArtifactResult> completedArtifacts,
            boolean reconciliationRetry
    ) {
        this(invocationId, execute, completedResultJson, completedArtifacts, reconciliationRetry, null);
    }
}
