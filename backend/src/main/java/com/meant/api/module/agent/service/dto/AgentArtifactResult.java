package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentArtifactType;
import java.time.Instant;
import java.util.UUID;

public record AgentArtifactResult(
        UUID artifactId,
        UUID messageId,
        UUID runId,
        AgentArtifactType type,
        int ordinal,
        String stableKey,
        String label,
        String canonicalProductKey,
        String offerKey,
        UUID inventoryItemId,
        UUID cartId,
        UUID cartLineId,
        UUID checkoutAttemptId,
        String payloadJson,
        Instant createdAt
) {
}
