package com.meant.api.module.agent.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SubmitAgentTurnCommand(
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 8000) String message,
        @Size(max = 120) String clientTurnId,
        @Valid VisibleProductContextCommand visibleProductContext,
        @Size(max = 128) String buyerIp
) {

    public SubmitAgentTurnCommand(
            UUID userId,
            UUID conversationId,
            String message,
            String clientTurnId
    ) {
        this(userId, conversationId, message, clientTurnId, null, null);
    }

    public SubmitAgentTurnCommand(
            UUID userId,
            UUID conversationId,
            String message,
            String clientTurnId,
            String buyerIp
    ) {
        this(userId, conversationId, message, clientTurnId, null, buyerIp);
    }
}
