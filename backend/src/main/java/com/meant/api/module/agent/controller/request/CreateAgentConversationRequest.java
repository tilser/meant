package com.meant.api.module.agent.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Creates a server-owned commerce agent conversation.")
public record CreateAgentConversationRequest(
        @Schema(
                description = "Optional conversation title. A title is derived from the first turn when omitted.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 120)
        String title
) {
}
