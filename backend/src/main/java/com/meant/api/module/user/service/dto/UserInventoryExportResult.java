package com.meant.api.module.user.service.dto;

import java.time.Instant;
import java.util.List;

public record UserInventoryExportResult(
        Instant exportedAt,
        List<UserInventoryItemResult> items
) {
}
