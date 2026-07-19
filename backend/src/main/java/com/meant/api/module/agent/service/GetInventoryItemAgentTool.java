package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.constant.AgentToolRisk;
import com.meant.api.module.agent.service.dto.AgentArtifact;
import com.meant.api.module.agent.service.dto.AgentToolDescriptor;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import com.meant.api.module.agent.service.dto.AgentToolExecutionResult;
import com.meant.api.module.agent.service.dto.GetInventoryItemAgentToolInput;
import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.UserInventoryProductRehydrationService;
import com.meant.api.module.user.service.dto.UserInventoryProductRehydrationResult;
import com.meant.api.module.user.service.query.RehydrateUserInventoryProductQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetInventoryItemAgentTool implements AgentTool {

    private static final AgentToolDescriptor DESCRIPTOR = new AgentToolDescriptor(
            "get_inventory_item",
            "Load a previously listed inventory item and rehydrate current product facts when available.",
            """
            {"type":"object","properties":{"inventoryItemId":{"type":"string","format":"uuid"}},"required":["inventoryItemId"],"additionalProperties":false}
            """,
            "1",
            AgentToolRisk.READ
    );

    private final AgentJsonSupport json;
    private final AgentProductReadReferenceService referenceService;
    private final UserCommerceContextService commerceContextService;
    private final UserInventoryProductRehydrationService rehydrationService;

    @Override
    public AgentToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, String argumentsJson) {
        GetInventoryItemAgentToolInput input = json.readArguments(argumentsJson, GetInventoryItemAgentToolInput.class);
        referenceService.requireInventoryItem(context, input.inventoryItemId());
        String country = commerceContextService.find(context.userId()).countryCode();
        UserInventoryProductRehydrationResult result = rehydrationService.rehydrate(
                new RehydrateUserInventoryProductQuery(context.userId(), input.inventoryItemId(), country));
        var commerce = result.commerceReference();
        AgentArtifact artifact = new AgentArtifact(
                AgentArtifactType.INVENTORY_ITEM,
                1,
                "inventory:" + result.inventoryItemId(),
                result.fallbackName(),
                commerce == null ? null : commerce.canonicalProductKey(),
                commerce == null ? null : commerce.offerKey(),
                result.inventoryItemId(), null, null, null,
                json.writeArtifact(result)
        );
        return AgentToolExecutionResult.read(
                json.write(result),
                result.currentFactsAvailable()
                        ? "Loaded the inventory item with current catalog facts."
                        : "Loaded the inventory-owned facts; current catalog facts were unavailable.",
                List.of(artifact)
        );
    }
}
