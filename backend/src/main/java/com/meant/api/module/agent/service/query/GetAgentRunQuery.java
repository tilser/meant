package com.meant.api.module.agent.service.query;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GetAgentRunQuery(
        @NotNull UUID userId,
        @NotNull UUID runId
) {
}
