package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.entity.UserTasteSignal;
import java.time.Instant;
import java.util.UUID;

public record UserTasteSignalResult(
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

    public static UserTasteSignalResult from(UserTasteSignal signal) {
        return new UserTasteSignalResult(
                signal.getId(),
                signal.getSignalType(),
                signal.getSignalKey(),
                signal.getLabel(),
                signal.getWeight(),
                signal.getPositiveCount(),
                signal.getNegativeCount(),
                signal.getLastBehavior(),
                signal.getSuggestedFilterId(),
                signal.getSuggestionStatus(),
                signal.getStatus(),
                signal.getCreatedAt(),
                signal.getUpdatedAt()
        );
    }
}
