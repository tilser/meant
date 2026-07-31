package com.meant.api.module.agent.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentTurnContext(
        AgentVisibleProductContext visibleProducts,
        AgentShelfContext shelf
) {

    public AgentTurnContext(AgentVisibleProductContext visibleProducts) {
        this(visibleProducts, null);
    }
}
