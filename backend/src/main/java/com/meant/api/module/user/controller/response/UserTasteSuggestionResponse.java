package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserTasteSuggestionResult;

public record UserTasteSuggestionResponse(
        String filterId,
        String label,
        String description,
        String reason,
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
