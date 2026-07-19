package com.meant.api.module.agent.service.dto;

public record AgentTurnContext(
        AgentVisibleProductContext visibleProducts,
        AgentProductClarification pendingProductClarification
) {

    public AgentTurnContext(AgentVisibleProductContext visibleProducts) {
        this(visibleProducts, null);
    }
}
