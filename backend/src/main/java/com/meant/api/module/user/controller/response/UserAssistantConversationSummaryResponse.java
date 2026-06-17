package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserAssistantConversationSummaryResult;
import java.time.Instant;
import java.util.UUID;

public record UserAssistantConversationSummaryResponse(
        UUID conversationId,
        String title,
        Instant createdAt,
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
