package com.meant.api.module.agent.service.dto;

import java.util.UUID;

/** Server-resolved product or inventory anchor that grounded a similarity search. */
public record AgentSimilarityAnchorResult(
        String canonicalProductKey,
        UUID inventoryItemId,
        String label,
        String query
) {
}
