package com.meant.api.module.discount.service.dto;

import com.meant.api.module.discount.constant.DiscountCodeSearchStatus;
import java.time.Instant;

public record DiscountCodeSearchCacheResult(
        DiscountCodeSearchStatus status,
        Instant searchedAt,
        Instant expiresAt,
        String errorMessage
) {
}
