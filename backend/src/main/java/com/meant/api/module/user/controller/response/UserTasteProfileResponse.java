package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import java.util.List;

public record UserTasteProfileResponse(
        String profileHash,
        List<UserTasteSignalResponse> signals,
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
