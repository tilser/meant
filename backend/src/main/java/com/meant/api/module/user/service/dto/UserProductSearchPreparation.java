package com.meant.api.module.user.service.dto;

import java.time.Instant;

public record UserProductSearchPreparation(
        String query,
        UserProductSearchQueryIntentResult queryIntent,
        UserSettingsResult settings,
        UserTasteProfileResult tasteProfile,
        UserProductSearchCatalogInput catalogInput,
        String normalizedQuery,
        String profileHash,
        Instant now,
        int offset,
        int limit,
        int fetchLimit
) {
}
