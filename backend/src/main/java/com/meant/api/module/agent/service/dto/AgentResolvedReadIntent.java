package com.meant.api.module.agent.service.dto;

import java.util.Objects;

public record AgentResolvedReadIntent(
        AgentModelToolCall toolCall,
        String subject
) {

    public AgentResolvedReadIntent {
        toolCall = Objects.requireNonNull(toolCall, "A resolved read intent requires a tool call");
        subject = Objects.requireNonNull(subject, "A resolved read intent requires a subject");
    }
}
