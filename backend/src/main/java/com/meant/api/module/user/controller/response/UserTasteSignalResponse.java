package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import java.time.Instant;
import java.util.UUID;

public record UserTasteSignalResponse(
        UUID id,
        UserTasteSignalType signalType,
        String signalKey,
        String label,
        double weight,
        int positiveCount,
        int negativeCount,
        String lastBehavior,
        String suggestedFilterId,
        UserTasteSuggestionStatus suggestionStatus,
        UserTasteSignalStatus status,
        Instant createdAt,
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
