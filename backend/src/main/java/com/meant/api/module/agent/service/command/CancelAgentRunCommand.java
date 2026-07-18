package com.meant.api.module.agent.service.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CancelAgentRunCommand(
        @NotNull UUID userId,
        @NotNull UUID runId
) {
}
