package com.meant.api.module.user.service.command;

import com.meant.api.module.user.service.dto.UserAssistantPageContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendUserAssistantMessageCommand(
        @NotNull
        UUID userId,

        UUID conversationId,

        @NotBlank
        @Size(max = 2000)
        String message,

        @Valid
        UserAssistantPageContext pageContext
) {
}
