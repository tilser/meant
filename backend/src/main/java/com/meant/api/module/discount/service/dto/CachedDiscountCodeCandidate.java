package com.meant.api.module.discount.service.dto;

import com.meant.api.module.discount.constant.DiscountCodeStatus;
import java.time.Instant;

public record CachedDiscountCodeCandidate(
        String code,
        DiscountCodeStatus status,
        Instant validUntil,
        Instant validatedAt,
        Instant expiresAt,
        String validationMessage
) {
}
