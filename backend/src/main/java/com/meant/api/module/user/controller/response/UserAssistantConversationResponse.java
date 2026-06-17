package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserAssistantConversationResult;
import java.util.List;
import java.util.UUID;

public record UserAssistantConversationResponse(
        UUID conversationId,
        List<UserAssistantMessageResponse> messages
) {

    public static UserAssistantConversationResponse from(UserAssistantConversationResult result) {
        return new UserAssistantConversationResponse(
                result.conversationId(),
                result.messages().stream()
                        .map(UserAssistantMessageResponse::from)
                        .toList()
        );
    }
}
