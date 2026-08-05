package com.meant.api.module.agent.service.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record IssueGuestConversationTransferCommand(
        @NotNull UUID guestUserId,
        @NotNull UUID conversationId
) {
}
