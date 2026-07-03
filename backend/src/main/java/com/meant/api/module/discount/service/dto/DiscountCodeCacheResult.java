package com.meant.api.module.discount.service.dto;

import java.time.Instant;
import java.util.List;

public record DiscountCodeCacheResult(
        Instant searchedAt,
        Instant expiresAt,
        List<DiscountCodeResult> codes
) {
}
