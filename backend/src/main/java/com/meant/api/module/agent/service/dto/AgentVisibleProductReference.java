package com.meant.api.module.agent.service.dto;

public record AgentVisibleProductReference(
        int visibleOrdinal,
        int resultOrdinal,
        String canonicalProductKey,
        String recommendedOfferKey,
        String title
) {
}
