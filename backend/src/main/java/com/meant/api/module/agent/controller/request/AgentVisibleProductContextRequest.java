package com.meant.api.module.agent.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(description = "The ordered product cards visible when the user submitted an agent turn.")
public record AgentVisibleProductContextRequest(
        @Schema(
                description = "The agent tool-result message that issued the visible product cards.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        UUID sourceMessageId,

        @Schema(
                description = "Visible canonical product keys in screen order, from left to right.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Size(min = 1, max = 4)
        List<@NotBlank @Size(max = 200) String> orderedCanonicalProductKeys
) {

    public AgentVisibleProductContextRequest {
        orderedCanonicalProductKeys = orderedCanonicalProductKeys == null
                ? List.of()
                : List.copyOf(orderedCanonicalProductKeys);
    }
}
