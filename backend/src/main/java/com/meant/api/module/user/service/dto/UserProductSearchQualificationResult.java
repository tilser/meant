package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserProductSearchFilterKind;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import java.util.List;
import java.util.UUID;

public record UserProductSearchQualificationResult(
        UUID qualificationId,
        UserProductSearchQualificationStatus status,
        String assistantMessage,
        List<String> suggestedReplies,
        List<UserProductSearchFilterKind> missingFilters,
        String effectiveQuery
) {
}
