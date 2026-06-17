package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductSearchResult;
import java.util.List;

public record UserProductSearchResponse(
        String query,
        String normalizedQuery,
        String profileHash,
        boolean cached,
        List<UserProductSearchProductResponse> products
) {

    public static UserProductSearchResponse from(UserProductSearchResult result) {
        return new UserProductSearchResponse(
                result.query(),
                result.normalizedQuery(),
                result.profileHash(),
                result.cached(),
                result.products().stream()
                        .map(UserProductSearchProductResponse::from)
                        .toList()
        );
    }
}
