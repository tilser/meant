package com.meant.api.module.agent.service.dto;

import java.util.List;

public record PickRecommendedProductAgentToolInput(List<String> canonicalProductKeys) {
    public PickRecommendedProductAgentToolInput {
        canonicalProductKeys = canonicalProductKeys == null ? List.of() : List.copyOf(canonicalProductKeys);
    }
}
