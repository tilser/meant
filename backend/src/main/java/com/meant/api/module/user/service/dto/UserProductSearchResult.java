package com.meant.api.module.user.service.dto;

import java.util.List;

public record UserProductSearchResult(
        String query,
        String normalizedQuery,
        String profileHash,
        boolean cached,
        int offset,
        int limit,
        Integer nextOffset,
        boolean hasMore,
        List<UserProductSearchProductResult> products
) {
}
