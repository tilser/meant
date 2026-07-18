package com.meant.api.module.agent.service.dto;

import com.meant.api.module.user.constant.UserInventoryCategory;
import com.meant.api.module.user.service.dto.UserInventoryCommerceReference;
import java.util.List;
import java.util.UUID;

public record AgentInventoryReferenceResult(
        int reference,
        UUID inventoryItemId,
        String name,
        String brand,
        UserInventoryCategory category,
        int quantity,
        String unit,
        String location,
        List<String> attributes,
        UserInventoryCommerceReference commerceReference
) {
    public AgentInventoryReferenceResult {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }
}
