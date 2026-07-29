package com.meant.api.module.agent.service.dto;

public record AgentExecutedToolCall(
        AgentModelToolResult modelResult,
        boolean successful,
        String waitingForUserMessage
) {

    public AgentExecutedToolCall(AgentModelToolResult modelResult, boolean successful) {
        this(modelResult, successful, null);
    }
}
