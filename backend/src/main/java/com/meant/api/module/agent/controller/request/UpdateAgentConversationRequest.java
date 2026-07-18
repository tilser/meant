package com.meant.api.module.agent.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Updates mutable conversation metadata.")
public record UpdateAgentConversationRequest(
        @Schema(
                description = "New conversation title.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(min = 1, max = 120)
        String title,

        @Schema(
                description = "Whether the conversation should be archived.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        Boolean archived
) {
}
