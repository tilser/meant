package com.meant.api.module.user.service.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ListUserAssistantConversationsQuery(
        @NotNull
        UUID userId,

        @Min(1)
        @Max(50)
        int limit
) {
}
