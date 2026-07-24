package com.meant.api.module.agent.service;

import com.meant.api.module.agent.entity.AgentArtifactReference;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.entity.AgentRunEvent;
import com.meant.api.module.agent.service.dto.AgentArtifactResult;
import com.meant.api.module.agent.service.dto.AgentConversationSummaryResult;
import com.meant.api.module.agent.service.dto.AgentMessageResult;
import com.meant.api.module.agent.service.dto.AgentRunEventResult;
import com.meant.api.module.agent.service.dto.AgentRunResult;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;

public final class AgentResultMapper {

    private AgentResultMapper() {
    }

    static AgentConversationSummaryResult conversation(AgentConversation source) {
        return new AgentConversationSummaryResult(
                source.getId(),
                MerchantBuyerTextSanitizer.sanitize(source.getTitle()),
                source.getStatus(),
                source.getMerchantId(),
                source.getActiveMissionId(),
                source.getLastSequenceNumber(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }

    static AgentMessageResult message(AgentMessage source) {
        return new AgentMessageResult(
                source.getId(),
                source.getRunId(),
                source.getSequenceNumber(),
                source.getRole(),
                source.getContentKind(),
                MerchantBuyerTextSanitizer.sanitize(source.getTextContent()),
                AgentBuyerPayloadSanitizer.sanitize(source.getContentJson()),
                source.getCorrelationId(),
                source.getCreatedAt()
        );
    }

    public static AgentArtifactResult artifact(AgentArtifactReference source) {
        return new AgentArtifactResult(
                source.getId(),
                source.getMessageId(),
                source.getRunId(),
                source.getArtifactType(),
                source.getOrdinal(),
                source.getStableKey(),
                MerchantBuyerTextSanitizer.sanitize(source.getLabel()),
                source.getCanonicalProductKey(),
                source.getOfferKey(),
                source.getInventoryItemId(),
                source.getCartId(),
                source.getCartLineId(),
                source.getCheckoutAttemptId(),
                AgentBuyerPayloadSanitizer.sanitize(source.getPayloadJson()),
                source.getCreatedAt()
        );
    }

    static AgentRunResult run(AgentRun source) {
        return new AgentRunResult(
                source.getId(),
                source.getConversationId(),
                source.getStatus(),
                source.getModel(),
                source.getPromptVersion(),
                source.getIterationCount(),
                source.getToolInvocationCount(),
                source.getInputTokens(),
                source.getOutputTokens(),
                source.getFailureCode(),
                MerchantBuyerTextSanitizer.sanitize(source.getSafeMessage()),
                source.isCancellationRequested(),
                source.getLastEventCursor(),
                source.getCreatedAt(),
                source.getStartedAt(),
                source.getCompletedAt()
        );
    }

    static AgentRunEventResult event(AgentRunEvent source) {
        return new AgentRunEventResult(
                source.getSchemaVersion(),
                source.getCursor(),
                source.getConversationId(),
                source.getRunId(),
                source.getEventType().wireValue(),
                source.getOccurredAt(),
                AgentBuyerPayloadSanitizer.sanitize(source.getPayloadJson())
        );
    }
}
