package com.meant.api.module.agent.service.dto;

public record AgentToolErrorPayload(
        boolean success,
        String code,
        String message,
        boolean retryable
) {
}
