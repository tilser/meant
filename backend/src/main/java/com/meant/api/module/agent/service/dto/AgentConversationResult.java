package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AgentConversationResult(
        UUID conversationId,
        String title,
        AgentConversationStatus status,
        String rollingSummary,
        int summaryVersion,
        UUID merchantId,
        UUID activeMissionId,
        long latestSequence,
        long latestCursor,
        List<AgentMessageResult> messages,
        List<AgentArtifactResult> artifacts,
        Instant createdAt,
        Instant updatedAt
) {

    public AgentConversationResult {
        messages = List.copyOf(messages);
        artifacts = List.copyOf(artifacts);
    }
}
