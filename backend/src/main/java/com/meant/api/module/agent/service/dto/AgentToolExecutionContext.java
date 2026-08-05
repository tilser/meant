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
        UUID merchantId,
        boolean anonymousUser
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
                null, null, null, null, null, null, null, false);
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
                idempotencyKey, null, null, null, null, null, null, false);
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
                idempotencyKey, executionOwner, null, null, null, null, null, false);
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
                idempotencyKey, executionOwner, buyerIp, null, null, null, null, false);
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
                idempotencyKey, executionOwner, buyerIp, null, null, visibleProductContext, null, false);
    }

    public AgentToolExecutionContext withIdempotencyKey(UUID value) {
        return copy(value, executionOwner, buyerIp, userAgent, language, visibleProductContext, merchantId,
                triggeringUserText);
    }

    public AgentToolExecutionContext withTriggeringUserText(String value) {
        return copy(idempotencyKey, executionOwner, buyerIp, userAgent, language, visibleProductContext, merchantId,
                value);
    }

    public AgentToolExecutionContext withExecutionOwner(UUID value) {
        return copy(idempotencyKey, value, buyerIp, userAgent, language, visibleProductContext, merchantId,
                triggeringUserText);
    }

    public AgentToolExecutionContext withBuyerIp(String value) {
        return copy(idempotencyKey, executionOwner, value, userAgent, language, visibleProductContext, merchantId,
                triggeringUserText);
    }

    public AgentToolExecutionContext withUserAgent(String value) {
        return copy(idempotencyKey, executionOwner, buyerIp, value, language, visibleProductContext, merchantId,
                triggeringUserText);
    }

    public AgentToolExecutionContext withLanguage(String value) {
        return copy(idempotencyKey, executionOwner, buyerIp, userAgent, value, visibleProductContext, merchantId,
                triggeringUserText);
    }

    public AgentToolExecutionContext withVisibleProductContext(AgentVisibleProductContext value) {
        return copy(idempotencyKey, executionOwner, buyerIp, userAgent, language, value, merchantId,
                triggeringUserText);
    }

    public AgentToolExecutionContext withMerchantId(UUID value) {
        return copy(idempotencyKey, executionOwner, buyerIp, userAgent, language, visibleProductContext, value,
                triggeringUserText);
    }

    public AgentToolExecutionContext withAnonymousUser(boolean value) {
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
                merchantId,
                value
        );
    }

    /**
     * Returns the durable buyer message that bounds qualification history. Direct UI actions do
     * not have a ledger message until after their tool completes, so their history has no cutoff.
     */
    public UUID qualificationContextMessageId() {
        return runId == null ? null : triggeringMessageId;
    }

    /** Stable server-issued identity shared by model turns and direct UI actions. */
    public UUID qualificationRequestId() {
        return runId == null ? idempotencyKey : triggeringMessageId;
    }

    private AgentToolExecutionContext copy(
            UUID nextIdempotencyKey,
            UUID nextExecutionOwner,
            String nextBuyerIp,
            String nextUserAgent,
            String nextLanguage,
            AgentVisibleProductContext nextVisibleProductContext,
            UUID nextMerchantId,
            String nextTriggeringUserText
    ) {
        return new AgentToolExecutionContext(
                userId,
                conversationId,
                runId,
                triggeringMessageId,
                nextTriggeringUserText,
                nextIdempotencyKey,
                nextExecutionOwner,
                nextBuyerIp,
                nextUserAgent,
                nextLanguage,
                nextVisibleProductContext,
                nextMerchantId,
                anonymousUser
        );
    }
}
