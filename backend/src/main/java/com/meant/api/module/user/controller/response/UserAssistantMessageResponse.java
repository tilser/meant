package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserAssistantMessageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record UserAssistantMessageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserProductSearchProductResponse> products,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
