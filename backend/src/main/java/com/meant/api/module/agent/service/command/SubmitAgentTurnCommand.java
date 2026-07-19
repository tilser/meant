package com.meant.api.module.agent.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SubmitAgentTurnCommand(
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 8000) String message,
        @Size(max = 120) String clientTurnId,
        @Size(max = 128) String buyerIp
) {

    public SubmitAgentTurnCommand(
            UUID userId,
            UUID conversationId,
            String message,
            String clientTurnId
    ) {
        this(userId, conversationId, message, clientTurnId, null);
    }
}
