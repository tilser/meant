package com.meant.api.module.agent.service.dto;

import java.util.UUID;

public record AgentToolExecutionContext(
        UUID userId,
        UUID conversationId,
        UUID runId,
        UUID triggeringMessageId,
        String triggeringUserText,
        UUID idempotencyKey,
        UUID executionOwner,
        String buyerIp,
        AgentVisibleProductContext visibleProductContext,
        AgentProductClarification pendingProductClarification
) {

    public AgentToolExecutionContext {
        buyerIp = buyerIp == null || buyerIp.isBlank() ? null : buyerIp.trim();
    }

    public AgentToolExecutionContext(
            UUID userId,
            UUID conversationId,
            UUID runId,
            UUID triggeringMessageId,
            String triggeringUserText
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText,
                null, null, null, null, null);
    }

    public AgentToolExecutionContext(
            UUID userId,
            UUID conversationId,
            UUID runId,
            UUID triggeringMessageId,
            String triggeringUserText,
            UUID idempotencyKey
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText,
                idempotencyKey, null, null, null, null);
    }

    public AgentToolExecutionContext(
            UUID userId,
            UUID conversationId,
            UUID runId,
            UUID triggeringMessageId,
            String triggeringUserText,
            UUID idempotencyKey,
            UUID executionOwner
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText,
                idempotencyKey, executionOwner, null, null, null);
    }

    public AgentToolExecutionContext(
            UUID userId,
            UUID conversationId,
            UUID runId,
            UUID triggeringMessageId,
            String triggeringUserText,
            UUID idempotencyKey,
            UUID executionOwner,
            String buyerIp
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText,
                idempotencyKey, executionOwner, buyerIp, null, null);
    }

    public AgentToolExecutionContext(
            UUID userId,
            UUID conversationId,
            UUID runId,
            UUID triggeringMessageId,
            String triggeringUserText,
            UUID idempotencyKey,
            UUID executionOwner,
            String buyerIp,
            AgentVisibleProductContext visibleProductContext
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText,
                idempotencyKey, executionOwner, buyerIp, visibleProductContext, null);
    }

    public AgentToolExecutionContext withIdempotencyKey(UUID value) {
        return new AgentToolExecutionContext(
                userId,
                conversationId,
                runId,
                triggeringMessageId,
                triggeringUserText,
                value,
                executionOwner,
                buyerIp,
                visibleProductContext,
                pendingProductClarification
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
                value,
                buyerIp,
                visibleProductContext,
                pendingProductClarification
        );
    }

    public AgentToolExecutionContext withBuyerIp(String value) {
        return new AgentToolExecutionContext(
                userId,
                conversationId,
                runId,
                triggeringMessageId,
                triggeringUserText,
                idempotencyKey,
                executionOwner,
                value,
                visibleProductContext,
                pendingProductClarification
        );
    }

    public AgentToolExecutionContext withVisibleProductContext(AgentVisibleProductContext value) {
        return new AgentToolExecutionContext(
                userId,
                conversationId,
                runId,
                triggeringMessageId,
                triggeringUserText,
                idempotencyKey,
                executionOwner,
                buyerIp,
                value,
                pendingProductClarification
        );
    }

    public AgentToolExecutionContext withPendingProductClarification(AgentProductClarification value) {
        return new AgentToolExecutionContext(
                userId,
                conversationId,
                runId,
                triggeringMessageId,
                triggeringUserText,
                idempotencyKey,
                executionOwner,
                buyerIp,
                visibleProductContext,
                value
        );
    }
}
