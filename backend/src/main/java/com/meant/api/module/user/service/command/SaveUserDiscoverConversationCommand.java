package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SaveUserDiscoverConversationCommand(
        @NotNull
        UUID userId,

        @NotNull
        UUID conversationId,

        @NotBlank
        @Size(max = 120)
        String title,

        @NotBlank
        @Size(max = 5_000_000)
        String threadJson,

        Long expectedRevision
) {
}
