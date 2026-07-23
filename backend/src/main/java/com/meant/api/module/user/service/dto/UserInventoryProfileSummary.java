package com.meant.api.module.user.service.dto;

import java.time.Instant;

public record UserInventoryProfileSummary(
        long itemCount,
        Instant lastUpdatedAt
) {
}
