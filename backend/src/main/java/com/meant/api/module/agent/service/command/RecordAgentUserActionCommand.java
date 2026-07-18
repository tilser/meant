package com.meant.api.module.agent.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RecordAgentUserActionCommand(
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 80) String toolName,
        @NotBlank @Size(max = 24000) String argumentsJson,
        @NotBlank @Size(max = 160) String idempotencyKey,
        @NotBlank @Size(max = 500) String summary
) {
}
