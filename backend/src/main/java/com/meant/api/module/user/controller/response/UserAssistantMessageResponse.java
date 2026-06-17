package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserAssistantMessageResult;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record UserAssistantMessageResponse(
        UUID id,
        String role,
        String content,
        List<UserProductSearchProductResponse> products,
        Instant createdAt
) {

    public static UserAssistantMessageResponse from(UserAssistantMessageResult result) {
        return new UserAssistantMessageResponse(
                result.id(),
                result.role().name().toLowerCase(Locale.ROOT),
                result.content(),
                result.products().stream()
                        .map(UserProductSearchProductResponse::from)
                        .toList(),
                result.createdAt()
        );
    }
}
