package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import java.util.UUID;

public record UserQualifiedProductSearchInput(
        UUID qualificationId,
        UUID conversationId,
        UUID merchantId,
        String effectiveQuery,
        CatalogDiscoveryFilters filters
) {
}
