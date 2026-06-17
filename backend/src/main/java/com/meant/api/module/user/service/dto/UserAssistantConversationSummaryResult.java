package com.meant.api.module.user.service.dto;

import java.time.Instant;
import java.util.UUID;

public record UserAssistantConversationSummaryResult(
        UUID conversationId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
}
