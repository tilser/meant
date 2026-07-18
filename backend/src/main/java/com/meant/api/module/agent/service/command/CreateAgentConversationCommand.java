package com.meant.api.module.agent.service.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateAgentConversationCommand(
        @NotNull UUID userId,
        @Size(max = 120) String title
) {
}
