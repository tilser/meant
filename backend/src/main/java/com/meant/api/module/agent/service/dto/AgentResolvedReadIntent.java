package com.meant.api.module.agent.service.dto;

import java.util.Objects;

public record AgentResolvedReadIntent(
        AgentModelToolCall toolCall,
        String subject,
        String trustedFollowOnAction
) {

    public AgentResolvedReadIntent {
        toolCall = Objects.requireNonNull(toolCall, "A resolved read intent requires a tool call");
        subject = Objects.requireNonNull(subject, "A resolved read intent requires a subject");
        trustedFollowOnAction = trustedFollowOnAction == null || trustedFollowOnAction.isBlank()
                ? null
                : trustedFollowOnAction.trim();
    }

    public AgentResolvedReadIntent(AgentModelToolCall toolCall, String subject) {
        this(toolCall, subject, null);
    }

    public boolean continueWithModelAfterSuccess() {
        return trustedFollowOnAction != null;
    }
}
