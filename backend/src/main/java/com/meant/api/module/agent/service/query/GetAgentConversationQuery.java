package com.meant.api.module.agent.service.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GetAgentConversationQuery(
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        @Min(0) long afterSequence,
        @Min(1) @Max(200) int limit
) {
}
