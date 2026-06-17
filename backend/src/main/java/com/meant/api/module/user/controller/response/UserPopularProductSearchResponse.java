package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserPopularProductSearchResult;

public record UserPopularProductSearchResponse(
        String displayQuery,
        String query
) {

    public static UserPopularProductSearchResponse from(UserPopularProductSearchResult result) {
        return new UserPopularProductSearchResponse(
                result.displayQuery(),
                result.query()
        );
    }
}
