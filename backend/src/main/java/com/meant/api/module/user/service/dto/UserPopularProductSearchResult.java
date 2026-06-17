package com.meant.api.module.user.service.dto;

import java.time.Instant;

public record UserPopularProductSearchResult(
        String displayQuery,
        String query,
        Long searchCount,
        Long distinctUserCount,
        Instant lastSearchedAt
) {
}
