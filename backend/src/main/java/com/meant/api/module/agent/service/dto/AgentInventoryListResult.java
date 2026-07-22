package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentInventoryListResult(
        List<AgentInventoryReferenceResult> items,
        boolean hasMore,
        boolean scanTruncated
) {
    public AgentInventoryListResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
