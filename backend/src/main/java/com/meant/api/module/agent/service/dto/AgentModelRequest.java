package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentModelRequest(
        String model,
        List<AgentModelMessage> messages,
        List<AgentModelToolDefinition> tools,
        double temperature,
        int maximumOutputTokens,
        String requiredToolName
) {

    public AgentModelRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        if (requiredToolName != null && tools.stream().noneMatch(tool -> requiredToolName.equals(tool.name()))) {
            throw new IllegalArgumentException("Required model tool must be present in the supplied tool definitions");
        }
    }

    public AgentModelRequest(
            String model,
            List<AgentModelMessage> messages,
            List<AgentModelToolDefinition> tools,
            double temperature,
            int maximumOutputTokens
    ) {
        this(model, messages, tools, temperature, maximumOutputTokens, null);
    }
}
