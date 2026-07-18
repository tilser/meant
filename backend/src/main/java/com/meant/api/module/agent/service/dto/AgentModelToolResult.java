package com.meant.api.module.agent.service.dto;

public record AgentModelToolResult(
        String toolCallId,
        String toolName,
        String resultJson
) {
}
