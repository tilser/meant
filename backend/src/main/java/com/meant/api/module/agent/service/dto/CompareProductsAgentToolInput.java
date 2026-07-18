package com.meant.api.module.agent.service.dto;

import java.util.List;

public record CompareProductsAgentToolInput(List<String> canonicalProductKeys) {
    public CompareProductsAgentToolInput {
        canonicalProductKeys = canonicalProductKeys == null ? List.of() : List.copyOf(canonicalProductKeys);
    }
}
