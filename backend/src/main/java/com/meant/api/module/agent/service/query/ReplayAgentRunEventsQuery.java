package com.meant.api.module.agent.service.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReplayAgentRunEventsQuery(
        @NotNull UUID userId,
        @NotNull UUID runId,
        @Min(0) long afterCursor,
        @Min(1) @Max(500) int limit
) {
}
