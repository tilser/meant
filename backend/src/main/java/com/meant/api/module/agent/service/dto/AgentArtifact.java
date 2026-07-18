package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentArtifactType;
import java.util.UUID;

public record AgentArtifact(
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
        String payloadJson
) {
}
