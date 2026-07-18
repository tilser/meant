package com.meant.api.module.agent.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Executes a typed direct UI action through the same tool adapter used by the agent.")
public record AgentUserActionRequest(
        @Schema(
                description = "Registered agent tool name.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 80)
        String toolName,

        @Schema(
                description = "JSON object matching the tool's published schema.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 24000)
        String argumentsJson,

        @Schema(
                description = "Stable client-generated key that makes retries return the original result.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 160)
        String idempotencyKey,

        @Schema(
                description = "User-visible description persisted into the conversation transcript.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 500)
        String summary
) {
}
