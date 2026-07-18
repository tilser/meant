package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentModelRole;
import java.util.List;

public record AgentModelMessage(
        AgentModelRole role,
        String text,
        List<AgentModelToolCall> toolCalls,
        List<AgentModelToolResult> toolResults
) {

    public AgentModelMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }

    public static AgentModelMessage system(String text) {
        return new AgentModelMessage(AgentModelRole.SYSTEM, text, List.of(), List.of());
    }

    public static AgentModelMessage user(String text) {
        return new AgentModelMessage(AgentModelRole.USER, text, List.of(), List.of());
    }

    public static AgentModelMessage assistant(String text, List<AgentModelToolCall> calls) {
        return new AgentModelMessage(AgentModelRole.ASSISTANT, text, calls, List.of());
    }

    public static AgentModelMessage tools(List<AgentModelToolResult> results) {
        return new AgentModelMessage(AgentModelRole.TOOL, null, List.of(), results);
    }
}
