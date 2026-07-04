package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record UserProductSearchResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String query,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String normalizedQuery,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String profileHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean cached,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int offset,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int limit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer nextOffset,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasMore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserProductSearchProductResponse> products
) {

    public static UserProductSearchResponse from(UserProductSearchResult result) {
        return new UserProductSearchResponse(
                result.query(),
                result.normalizedQuery(),
                result.profileHash(),
                result.cached(),
                result.offset(),
                result.limit(),
                result.nextOffset(),
                result.hasMore(),
                result.products().stream()
                        .map(UserProductSearchProductResponse::from)
                        .toList()
        );
    }
}
