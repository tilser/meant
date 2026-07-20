package com.meant.api.module.agent.service.dto;

public record AgentTurnContext(
        AgentVisibleProductContext visibleProducts,
        AgentShelfContext shelf,
        AgentProductClarification pendingProductClarification
) {

    public AgentTurnContext(AgentVisibleProductContext visibleProducts) {
        this(visibleProducts, null, null);
    }

    public AgentTurnContext(
            AgentVisibleProductContext visibleProducts,
            AgentProductClarification pendingProductClarification
    ) {
        this(visibleProducts, null, pendingProductClarification);
    }
}
