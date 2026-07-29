package com.meant.api.module.agent.service.dto;

import com.meant.api.common.util.AcceptLanguageParser;
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
        String userAgent,
        String language,
        AgentVisibleProductContext visibleProductContext,
        AgentProductClarification pendingProductClarification,
        UUID merchantId
) {

    public AgentToolExecutionContext {
        buyerIp = buyerIp == null || buyerIp.isBlank() ? null : buyerIp.trim();
        userAgent = userAgent == null || userAgent.isBlank() ? null : userAgent.trim();
        language = AcceptLanguageParser.canonicalLanguageTag(language);
    }

    public AgentToolExecutionContext(
            UUID userId,
            UUID conversationId,
            UUID runId,
            UUID triggeringMessageId,
            String triggeringUserText
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText,
                null, null, null, null, null, null, null, null);
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
                idempotencyKey, null, null, null, null, null, null, null);
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
                idempotencyKey, executionOwner, null, null, null, null, null, null);
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
                idempotencyKey, executionOwner, buyerIp, null, null, null, null, null);
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
                idempotencyKey, executionOwner, buyerIp, null, null,
                visibleProductContext, null, null);
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
            AgentVisibleProductContext visibleProductContext,
            AgentProductClarification pendingProductClarification
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText,
                idempotencyKey, executionOwner, buyerIp, null, null, visibleProductContext,
                pendingProductClarification, null);
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
            AgentVisibleProductContext visibleProductContext,
            AgentProductClarification pendingProductClarification,
            UUID merchantId
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText,
                idempotencyKey, executionOwner, buyerIp, null, null, visibleProductContext,
                pendingProductClarification, merchantId);
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
            String userAgent,
            AgentVisibleProductContext visibleProductContext,
            AgentProductClarification pendingProductClarification,
            UUID merchantId
    ) {
        this(userId, conversationId, runId, triggeringMessageId, triggeringUserText,
                idempotencyKey, executionOwner, buyerIp, userAgent, null, visibleProductContext,
                pendingProductClarification, merchantId);
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
                userAgent,
                language,
                visibleProductContext,
                pendingProductClarification,
                merchantId
        );
    }

    public AgentToolExecutionContext withTriggeringUserText(String value) {
        return new AgentToolExecutionContext(
                userId,
                conversationId,
                runId,
                triggeringMessageId,
                value,
                idempotencyKey,
                executionOwner,
                buyerIp,
                userAgent,
                language,
                visibleProductContext,
                pendingProductClarification,
                merchantId
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
                userAgent,
                language,
                visibleProductContext,
                pendingProductClarification,
                merchantId
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
                userAgent,
                language,
                visibleProductContext,
                pendingProductClarification,
                merchantId
        );
    }

    public AgentToolExecutionContext withUserAgent(String value) {
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
                language,
                visibleProductContext,
                pendingProductClarification,
                merchantId
        );
    }

    public AgentToolExecutionContext withLanguage(String value) {
        return new AgentToolExecutionContext(
                userId,
                conversationId,
                runId,
                triggeringMessageId,
                triggeringUserText,
                idempotencyKey,
                executionOwner,
                buyerIp,
                userAgent,
                value,
                visibleProductContext,
                pendingProductClarification,
                merchantId
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
                userAgent,
                language,
                value,
                pendingProductClarification,
                merchantId
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
                userAgent,
                language,
                visibleProductContext,
                value,
                merchantId
        );
    }

    public AgentToolExecutionContext withMerchantId(UUID value) {
        return new AgentToolExecutionContext(
                userId,
                conversationId,
                runId,
                triggeringMessageId,
                triggeringUserText,
                idempotencyKey,
                executionOwner,
                buyerIp,
                userAgent,
                language,
                visibleProductContext,
                pendingProductClarification,
                value
        );
    }
}
