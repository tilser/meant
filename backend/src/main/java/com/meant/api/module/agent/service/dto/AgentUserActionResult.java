package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentUserActionResult(
        AgentMessageResult message,
        String resultJson,
        List<AgentArtifactResult> artifacts
) {

    public AgentUserActionResult {
        artifacts = List.copyOf(artifacts);
    }
}
