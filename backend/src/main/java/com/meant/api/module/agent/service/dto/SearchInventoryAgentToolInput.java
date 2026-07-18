package com.meant.api.module.agent.service.dto;

public record SearchInventoryAgentToolInput(
        String query,
        String category,
        Boolean restockOnly,
        Integer limit
) {
}
