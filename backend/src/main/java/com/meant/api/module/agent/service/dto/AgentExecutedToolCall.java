package com.meant.api.module.agent.service.dto;

public record AgentExecutedToolCall(
        AgentModelToolResult modelResult,
        boolean successful
) {
}
