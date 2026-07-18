package com.meant.api.module.agent.service.dto;

import java.util.UUID;

public record AgentToolExecutionContext(
        UUID userId,
        UUID conversationId,
        UUID runId,
        UUID triggeringMessageId,
        String triggeringUserText,
        UUID idempotencyKey,
        UUID executionOwner
) {

    public AgentToolExecutionContext(
            UUID userId,
            UUID conversationId,
            UUID runId,
            UUID triggeringMessageId,
            String triggeringUserText
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText, null, null);
    }

    public AgentToolExecutionContext(
            UUID userId,
            UUID conversationId,
            UUID runId,
            UUID triggeringMessageId,
            String triggeringUserText,
            UUID idempotencyKey
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText, idempotencyKey, null);
    }

    public AgentToolExecutionContext withIdempotencyKey(UUID value) {
        return new AgentToolExecutionContext(
                userId,
                conversationId,
                runId,
                triggeringMessageId,
                triggeringUserText,
                value,
                executionOwner
        );
    }

    public AgentToolExecutionContext withExecutionOwner(UUID value) {
        return new AgentToolExecutionContext(
                userId,
                conversationId,
                runId,
                triggeringMessageId,
                triggeringUserText,
                idempotencyKey,
                value
        );
    }
}
