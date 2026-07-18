package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import java.time.Instant;
import java.util.UUID;

public record AgentConversationSummaryResult(
        UUID conversationId,
        String title,
        AgentConversationStatus status,
        UUID activeMissionId,
        long latestSequence,
        Instant createdAt,
        Instant updatedAt
) {
}
