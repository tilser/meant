package com.meant.api.module.agent.service.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ListAgentConversationsQuery(
        @NotNull UUID userId,
        boolean archived,
        @Min(1) @Max(100) int limit
) {
}
