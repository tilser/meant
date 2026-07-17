package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.constant.UserProductSearchFilterKind;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Qualification state that either asks for more input or authorizes catalog discovery.")
public record UserProductSearchQualificationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID qualificationId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserProductSearchQualificationStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String assistantMessage,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> suggestedReplies,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserProductSearchFilterKind> missingFilters,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String effectiveQuery
) {

    public static UserProductSearchQualificationResponse from(UserProductSearchQualificationResult result) {
        return new UserProductSearchQualificationResponse(
                result.qualificationId(),
                result.status(),
                result.assistantMessage(),
                result.suggestedReplies(),
                result.missingFilters(),
                result.effectiveQuery()
        );
    }
}
