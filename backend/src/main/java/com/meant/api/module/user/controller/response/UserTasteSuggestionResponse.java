package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserTasteSuggestionResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserTasteSuggestionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String filterId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double score
) {

    public static UserTasteSuggestionResponse from(UserTasteSuggestionResult result) {
        return new UserTasteSuggestionResponse(
                result.filterId(),
                result.label(),
                result.description(),
                result.reason(),
                result.score()
        );
    }
}
