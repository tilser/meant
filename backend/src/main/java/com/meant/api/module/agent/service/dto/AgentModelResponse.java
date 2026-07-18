package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentModelResponse(
        String text,
        List<AgentModelToolCall> toolCalls,
        AgentModelUsage usage,
        String finishReason,
        String resolvedModel
) {

    public AgentModelResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? new AgentModelUsage(null, null) : usage;
    }
}
