package com.meant.api.module.discount.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DiscountCodeSearchResult(
        UUID merchantId,
        String merchantDomain,
        boolean cached,
        Instant searchedAt,
        Instant expiresAt,
        List<DiscountCodeResult> codes
) {
}
