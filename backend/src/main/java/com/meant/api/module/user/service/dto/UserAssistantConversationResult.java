package com.meant.api.module.user.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserAssistantConversationResult(
        UUID conversationId,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<UserAssistantMessageResult> messages
) {

    public UserAssistantConversationResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
