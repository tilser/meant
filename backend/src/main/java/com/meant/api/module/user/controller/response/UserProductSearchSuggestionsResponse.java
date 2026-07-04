package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductSearchSuggestionsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record UserProductSearchSuggestionsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> suggestions
) {

    public static UserProductSearchSuggestionsResponse from(UserProductSearchSuggestionsResult result) {
        return new UserProductSearchSuggestionsResponse(result.suggestions());
    }
}
