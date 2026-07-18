package com.meant.api.module.agent.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Queues a user turn for the controlled commerce agent run loop.")
public record SubmitAgentTurnRequest(
        @Schema(
                description = "The user's natural-language message.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 8000)
        String message,

        @Schema(
                description = "Optional client correlation identifier for reconnect-safe submission.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 120)
        String clientTurnId
) {
}
