package com.meant.api.module.agent.service.dto;

import java.time.Instant;
import java.util.UUID;

public record AgentSimilaritySearchQualificationContext(
        UUID qualificationId,
        UUID userId,
        UUID conversationId,
        UUID merchantId,
        String canonicalProductKey,
        UUID inventoryItemId,
        String anchorLabel,
        String initialUserText,
        Instant createdAt
) {
}
