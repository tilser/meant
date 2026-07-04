package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UserAssistantChatRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 2000)
        String message,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Valid
        UserAssistantChatContextRequest context
) {
}
