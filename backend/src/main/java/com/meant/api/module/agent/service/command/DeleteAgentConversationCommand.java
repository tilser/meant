package com.meant.api.module.agent.service.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DeleteAgentConversationCommand(
        @NotNull UUID userId,
        @NotNull UUID conversationId
) {
}
