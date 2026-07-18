package com.meant.api.module.agent.service.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateAgentConversationCommand(
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        @Size(min = 1, max = 120) String title,
        Boolean archived
) {
}
