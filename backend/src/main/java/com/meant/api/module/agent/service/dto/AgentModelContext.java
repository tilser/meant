package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentModelContext(
        List<AgentModelMessage> messages,
        String triggeringUserText
) {

    public AgentModelContext {
        messages = List.copyOf(messages);
    }
}
