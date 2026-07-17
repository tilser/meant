package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserDiscoverConversationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Persisted Discover chat snapshot.")
public record UserDiscoverConversationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID conversationId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String threadJson,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long revision
) {

    public static UserDiscoverConversationResponse from(UserDiscoverConversationResult result) {
        return new UserDiscoverConversationResponse(
                result.conversationId(),
                result.title(),
                result.createdAt(),
                result.updatedAt(),
                result.threadJson(),
                result.revision()
        );
    }
}
