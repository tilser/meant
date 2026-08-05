package com.meant.api.module.agent.controller.response;

import com.meant.api.module.agent.service.dto.GuestConversationTransferToken;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "One-time guest conversation transfer capability.")
public record GuestConversationTransferTokenResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt
) {

    public static GuestConversationTransferTokenResponse from(GuestConversationTransferToken result) {
        return new GuestConversationTransferTokenResponse(result.token(), result.conversationId(), result.expiresAt());
    }
}
