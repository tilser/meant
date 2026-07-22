package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentInventoryArtifactKind;
import com.meant.api.module.user.service.dto.UserInventoryProductRehydrationResult;

public record AgentInventorySelectedItemArtifact(
        AgentInventoryArtifactKind kind,
        UserInventoryProductRehydrationResult item
) {
}
