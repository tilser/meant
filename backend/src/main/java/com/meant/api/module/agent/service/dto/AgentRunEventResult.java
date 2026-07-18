package com.meant.api.module.agent.service.dto;

import java.time.Instant;
import java.util.UUID;

public record AgentRunEventResult(
        int schemaVersion,
        long cursor,
        UUID conversationId,
        UUID runId,
        String type,
        Instant occurredAt,
        String payloadJson
) {
}
