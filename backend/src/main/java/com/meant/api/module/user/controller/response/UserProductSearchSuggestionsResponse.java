package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductSearchSuggestionsResult;
import java.util.List;

public record UserProductSearchSuggestionsResponse(
        List<String> suggestions
) {

    public static UserProductSearchSuggestionsResponse from(UserProductSearchSuggestionsResult result) {
        return new UserProductSearchSuggestionsResponse(result.suggestions());
    }
}
