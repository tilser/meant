package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentInventoryListResult(List<AgentInventoryReferenceResult> items) {
    public AgentInventoryListResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
