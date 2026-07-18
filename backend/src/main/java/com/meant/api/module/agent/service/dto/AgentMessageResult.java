package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentMessageRole;
import java.time.Instant;
import java.util.UUID;

public record AgentMessageResult(
        UUID messageId,
        UUID runId,
        long sequenceNumber,
        AgentMessageRole role,
        AgentContentKind contentKind,
        String textContent,
        String contentJson,
        String correlationId,
        Instant createdAt
) {
}
