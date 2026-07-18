package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentProductInteractionListResult(List<AgentProductInteractionResult> products) {

    public AgentProductInteractionListResult {
        products = products == null ? List.of() : List.copyOf(products);
    }
}
