package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentModelRequest(
        String model,
        List<AgentModelMessage> messages,
        List<AgentModelToolDefinition> tools,
        double temperature,
        int maximumOutputTokens
) {

    public AgentModelRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
