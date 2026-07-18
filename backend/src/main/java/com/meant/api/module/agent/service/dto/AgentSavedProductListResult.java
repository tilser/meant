package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentSavedProductListResult(List<AgentSavedProductReferenceResult> products) {
    public AgentSavedProductListResult {
        products = products == null ? List.of() : List.copyOf(products);
    }
}
