package com.meant.api.module.agent.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ClaimGuestConversationTransferCommand(
        @NotNull UUID targetUserId,
        @NotBlank @Size(max = 128) String token
) {
}
