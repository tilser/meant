package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserAssistantStreamEvent;
import java.util.List;
import java.util.UUID;

public record UserAssistantStreamEventResponse(
        String type,
        UUID conversationId,
        UUID messageId,
        String text,
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
