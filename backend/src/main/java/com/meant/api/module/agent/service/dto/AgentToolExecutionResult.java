package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentRunEventType;
import java.util.List;

public record AgentToolExecutionResult(
        String resultJson,
        String safeSummary,
        List<AgentArtifact> artifacts,
        AgentRunEventType domainEventType
) {

    public AgentToolExecutionResult {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public static AgentToolExecutionResult read(String resultJson, String summary, List<AgentArtifact> artifacts) {
        return new AgentToolExecutionResult(resultJson, summary, artifacts, null);
    }
}
