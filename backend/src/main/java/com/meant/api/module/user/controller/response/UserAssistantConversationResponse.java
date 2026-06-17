package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserAssistantConversationResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserAssistantConversationResponse(
        UUID conversationId,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<UserAssistantMessageResponse> messages
) {

    public static UserAssistantConversationResponse from(UserAssistantConversationResult result) {
        return new UserAssistantConversationResponse(
                result.conversationId(),
                result.title(),
                result.createdAt(),
                result.updatedAt(),
                result.messages().stream()
                        .map(UserAssistantMessageResponse::from)
                        .toList()
        );
    }
}
