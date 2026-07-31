package com.meant.api.module.agent.service.dto;

import com.meant.api.module.user.constant.UserProductSearchDecisionSource;
import java.util.List;

public record AgentAppliedSearchFilter(
        List<String> values,
        UserProductSearchDecisionSource source
) {

    public AgentAppliedSearchFilter {
        values = values == null ? List.of() : List.copyOf(values);
        source = source == null ? UserProductSearchDecisionSource.NONE : source;
    }
}
