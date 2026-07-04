package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserAssistantConversationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserAssistantConversationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserAssistantMessageResponse> messages
) {

    public static UserAssistantConversationResponse from(UserAssistantConversationResult result) {
        return new UserAssistantConversationResponse(
                result.conversationId(),
                result.title(),
                result.createdAt(),
                result.updatedAt(),
                result.messages().stream()
                        .map(UserAssistantMessageResponse::from)
                        .toList()
        );
    }
}
