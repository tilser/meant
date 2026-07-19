package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentModelContext(
        List<AgentModelMessage> messages,
        String triggeringUserText,
        AgentVisibleProductContext visibleProductContext,
        AgentProductClarification pendingProductClarification
) {

    public AgentModelContext {
        messages = List.copyOf(messages);
    }

    public AgentModelContext(List<AgentModelMessage> messages, String triggeringUserText) {
        this(messages, triggeringUserText, null, null);
    }

    public AgentModelContext(
            List<AgentModelMessage> messages,
            String triggeringUserText,
            AgentVisibleProductContext visibleProductContext
    ) {
        this(messages, triggeringUserText, visibleProductContext, null);
    }
}
