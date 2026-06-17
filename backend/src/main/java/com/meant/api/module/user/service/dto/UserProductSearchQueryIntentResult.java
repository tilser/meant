package com.meant.api.module.user.service.dto;

import java.util.List;

public record UserProductSearchQueryIntentResult(
        String originalQuery,
        String normalizedOriginalQuery,
        String searchQuery,
        String normalizedSearchQuery,
        String intentCacheKey,
        List<String> constraints,
        List<String> preferenceHints,
        String confidence,
        String source
) {
}
