package com.meant.api.module.agent.service.dto;

import java.time.Instant;
import java.util.UUID;

public record GuestConversationTransferToken(
        String token,
        UUID conversationId,
        Instant expiresAt
) {
}
