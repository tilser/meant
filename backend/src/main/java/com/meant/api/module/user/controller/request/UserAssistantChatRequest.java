package com.meant.api.module.user.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UserAssistantChatRequest(
        UUID conversationId,

        @NotBlank
        @Size(max = 2000)
        String message,

        @Valid
        UserAssistantChatContextRequest context
) {
}
