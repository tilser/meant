package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record UserTasteSignalResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserTasteSignalType signalType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String signalKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double weight,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int positiveCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int negativeCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String lastBehavior,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String suggestedFilterId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserTasteSuggestionStatus suggestionStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserTasteSignalStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {

    public static UserTasteSignalResponse from(UserTasteSignalResult result) {
        return new UserTasteSignalResponse(
                result.id(),
                result.signalType(),
                result.signalKey(),
                result.label(),
                result.weight(),
                result.positiveCount(),
                result.negativeCount(),
                result.lastBehavior(),
                result.suggestedFilterId(),
                result.suggestionStatus(),
                result.status(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
