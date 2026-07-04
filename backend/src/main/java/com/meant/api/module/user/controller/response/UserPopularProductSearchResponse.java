package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserPopularProductSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserPopularProductSearchResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String displayQuery,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String query
) {

    public static UserPopularProductSearchResponse from(UserPopularProductSearchResult result) {
        return new UserPopularProductSearchResponse(
                result.displayQuery(),
                result.query()
        );
    }
}
