package com.meant.api.module.agent.service.dto;

public record AgentModelUsage(
        Long inputTokens,
        Long outputTokens
) {
}
