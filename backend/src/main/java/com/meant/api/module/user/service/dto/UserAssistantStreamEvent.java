package com.meant.api.module.user.service.dto;

import java.util.List;
import java.util.UUID;

public record UserAssistantStreamEvent(
        String type,
        UUID conversationId,
        UUID messageId,
        String text,
        List<UserProductSearchProductResult> products
) {

    public UserAssistantStreamEvent {
        products = products == null ? List.of() : List.copyOf(products);
    }

    public static UserAssistantStreamEvent metadata(UUID conversationId) {
        return new UserAssistantStreamEvent("metadata", conversationId, null, null, List.of());
    }

    public static UserAssistantStreamEvent delta(String text) {
        return new UserAssistantStreamEvent("delta", null, null, text, List.of());
    }

    public static UserAssistantStreamEvent done(
            UUID conversationId,
            UUID messageId,
            String text,
            List<UserProductSearchProductResult> products
    ) {
        return new UserAssistantStreamEvent("done", conversationId, messageId, text, products);
    }

    public static UserAssistantStreamEvent error(String text) {
        return new UserAssistantStreamEvent("error", null, null, text, List.of());
    }
}
