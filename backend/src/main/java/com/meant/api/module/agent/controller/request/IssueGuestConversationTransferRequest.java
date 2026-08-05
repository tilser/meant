package com.meant.api.module.agent.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Issues a short-lived capability for importing the active guest conversation.")
public record IssueGuestConversationTransferRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UUID conversationId
) {
}
