package com.meant.api.module.agent.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Caller input for resolving a reply against the persisted product-search qualification. */
public record ResolveAgentPendingProductSearchQuery(
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        UUID merchantId,
        @NotNull UUID triggeringMessageId,
        @NotBlank @Size(max = 8000) String currentTurn
) {
}
