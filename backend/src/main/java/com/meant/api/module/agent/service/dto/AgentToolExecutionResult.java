package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentRunEventType;
import java.util.List;

public record AgentToolExecutionResult(
        String resultJson,
        String safeSummary,
        List<AgentArtifact> artifacts,
        AgentRunEventType domainEventType,
        String waitingForUserMessage
) {

    public AgentToolExecutionResult {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        waitingForUserMessage = waitingForUserMessage == null || waitingForUserMessage.isBlank()
                ? null
                : waitingForUserMessage.trim();
    }

    public AgentToolExecutionResult(
            String resultJson,
            String safeSummary,
            List<AgentArtifact> artifacts,
            AgentRunEventType domainEventType
    ) {
        this(resultJson, safeSummary, artifacts, domainEventType, null);
    }

    public static AgentToolExecutionResult read(String resultJson, String summary, List<AgentArtifact> artifacts) {
        return new AgentToolExecutionResult(resultJson, summary, artifacts, null);
    }

    public static AgentToolExecutionResult waitingForUser(String resultJson, String message) {
        return new AgentToolExecutionResult(resultJson, message, List.of(), null, message);
    }
}
