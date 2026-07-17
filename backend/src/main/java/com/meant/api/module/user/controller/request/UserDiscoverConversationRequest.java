package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Persisted Discover chat snapshot.")
public record UserDiscoverConversationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 120)
        String title,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 5_000_000)
        String threadJson,

        @Schema(
                description = "Last server revision observed by the caller; omitted only when creating a new chat",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        Long expectedRevision
) {
}
