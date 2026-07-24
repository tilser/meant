package com.meant.api.module.agent.controller.response;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.service.dto.AgentConversationResult;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Server-owned conversation snapshot for load and cursor recovery.")
public record AgentConversationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AgentConversationStatus status,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String rollingSummary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int summaryVersion,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID activeMissionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long latestSequence,
        @Schema(
                description = "Oldest running or queued run in this conversation, if one is active",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        UUID currentRunId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long latestCursor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AgentMessageResponse> messages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AgentArtifactResponse> artifacts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {

    public static AgentConversationResponse from(AgentConversationResult result) {
        return new AgentConversationResponse(
                result.conversationId(),
                MerchantBuyerTextSanitizer.sanitize(result.title()),
                result.status(),
                MerchantBuyerTextSanitizer.sanitize(result.rollingSummary()),
                result.summaryVersion(),
                result.merchantId(),
                result.activeMissionId(),
                result.latestSequence(),
                result.currentRunId(),
                result.latestCursor(),
                result.messages().stream().map(AgentMessageResponse::from).toList(),
                result.artifacts().stream().map(AgentArtifactResponse::from).toList(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
