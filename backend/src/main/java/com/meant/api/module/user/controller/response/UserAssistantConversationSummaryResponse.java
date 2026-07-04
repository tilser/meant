package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserAssistantConversationSummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record UserAssistantConversationSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {

    public static UserAssistantConversationSummaryResponse from(UserAssistantConversationSummaryResult result) {
        return new UserAssistantConversationSummaryResponse(
                result.conversationId(),
                result.title(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
