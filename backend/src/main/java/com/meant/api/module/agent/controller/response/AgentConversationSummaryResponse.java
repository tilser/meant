package com.meant.api.module.agent.controller.response;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.service.dto.AgentConversationSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Agent conversation list item.")
public record AgentConversationSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AgentConversationStatus status,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID activeMissionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long latestSequence,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
) {

    public static AgentConversationSummaryResponse from(AgentConversationSummaryResult result) {
        return new AgentConversationSummaryResponse(
                result.conversationId(),
                result.title(),
                result.status(),
                result.activeMissionId(),
                result.latestSequence(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
