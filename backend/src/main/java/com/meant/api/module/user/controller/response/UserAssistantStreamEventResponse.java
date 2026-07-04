package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserAssistantStreamEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record UserAssistantStreamEventResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID messageId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String text,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserProductSearchProductResponse> products
) {

    public static UserAssistantStreamEventResponse from(UserAssistantStreamEvent event) {
        return new UserAssistantStreamEventResponse(
                event.type(),
                event.conversationId(),
                event.messageId(),
                event.text(),
                event.products().stream()
                        .map(UserProductSearchProductResponse::from)
                        .toList()
        );
    }

    public static UserAssistantStreamEventResponse error(String text) {
        return new UserAssistantStreamEventResponse("error", null, null, text, List.of());
    }
}
