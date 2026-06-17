package com.meant.api.module.user.service.query;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GetUserAssistantConversationQuery(
        @NotNull
        UUID userId,

        @NotNull
        UUID conversationId
) {
}
