package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record UserTasteProfileResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String profileHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserTasteSignalResponse> signals,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserTasteSuggestionResponse> suggestions
) {

    public static UserTasteProfileResponse from(UserTasteProfileResult result) {
        return new UserTasteProfileResponse(
                result.profileHash(),
                result.signals().stream().map(UserTasteSignalResponse::from).toList(),
                result.suggestions().stream().map(UserTasteSuggestionResponse::from).toList()
        );
    }
}
