package com.meant.api.module.agent.service.dto;

import java.time.Instant;

public record AgentProductInteractionResult(
        String canonicalProductKey,
        String offerKey,
        boolean pinned,
        String pinnedOfferKey,
        Instant pinnedAt,
        boolean watched,
        String watchedOfferKey,
        Instant watchedAt,
        Instant updatedAt
) {
}
