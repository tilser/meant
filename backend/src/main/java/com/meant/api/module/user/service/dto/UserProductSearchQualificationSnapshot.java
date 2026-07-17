package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import java.time.Instant;
import java.util.UUID;

public record UserProductSearchQualificationSnapshot(
        UUID qualificationId,
        UUID userId,
        UUID conversationId,
        UUID merchantId,
        String originalQuery,
        UserProductSearchQualificationStatus status,
        UserProductSearchQualificationPlan plan,
        String model,
        String promptVersion,
        Instant createdAt,
        Instant updatedAt
) {
}
