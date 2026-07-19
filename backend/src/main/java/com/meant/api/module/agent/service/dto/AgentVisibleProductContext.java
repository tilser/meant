package com.meant.api.module.agent.service.dto;

import java.util.List;
import java.util.UUID;

public record AgentVisibleProductContext(
        UUID sourceMessageId,
        List<AgentVisibleProductReference> products
) {

    public AgentVisibleProductContext {
        products = products == null ? List.of() : List.copyOf(products);
    }
}
