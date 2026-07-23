package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentProductClarificationEvaluation(
        boolean clarificationRequired,
        List<AgentVisibleProductReference> candidates
) {

    public AgentProductClarificationEvaluation {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
