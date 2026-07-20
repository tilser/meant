package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentShelfItemKind;
import java.util.List;

public record AgentShelfItem(
        AgentShelfItemKind kind,
        String canonicalProductKey,
        String title,
        String text,
        List<String> relatedProductNames
) {

    public AgentShelfItem {
        relatedProductNames = relatedProductNames == null ? List.of() : List.copyOf(relatedProductNames);
    }
}
