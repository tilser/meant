package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentProductReferenceResult(
        int reference,
        String canonicalProductKey,
        String title,
        String description,
        String imageUrl,
        String recommendedOfferKey,
        List<AgentOfferReferenceResult> offers
) {
    public AgentProductReferenceResult {
        offers = offers == null ? List.of() : List.copyOf(offers);
    }
}
