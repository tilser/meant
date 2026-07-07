package com.meant.api.module.user.service.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDiscoverConversationResult(
        UUID conversationId,
        String title,
        Instant createdAt,
        Instant updatedAt,
        String threadJson
) {
}
