package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentInventoryArtifactKind;
import com.meant.api.module.user.service.dto.UserInventoryItemResult;

/** Server-owned inventory result plus the completeness needed for safe item resolution. */
public record AgentInventorySearchArtifact(
        AgentInventoryArtifactKind kind,
        UserInventoryItemResult item,
        boolean hasMore,
        boolean scanTruncated
) {
}
