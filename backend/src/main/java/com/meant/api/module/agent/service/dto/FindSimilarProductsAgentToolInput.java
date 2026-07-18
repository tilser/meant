package com.meant.api.module.agent.service.dto;

import java.util.UUID;

public record FindSimilarProductsAgentToolInput(
        String canonicalProductKey,
        UUID inventoryItemId,
        String query
) {
}
