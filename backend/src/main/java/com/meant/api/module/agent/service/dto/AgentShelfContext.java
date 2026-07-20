package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentShelfContext(List<AgentShelfItem> items) {

    public AgentShelfContext {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
