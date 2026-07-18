package com.meant.api.module.agent.service.dto;

public record AgentProductInteractionReference(
        String canonicalProductKey,
        String offerKey,
        String label
) {
}
