package com.meant.api.module.discount.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Buyer-facing discount search projection shared by HTTP and agent boundaries. */
public record BuyerDiscountCodeSearchResult(
        UUID merchantId,
        String merchantOrigin,
        boolean cached,
        Instant searchedAt,
        Instant expiresAt,
        List<BuyerDiscountCodeResult> codes
) {

    public BuyerDiscountCodeSearchResult {
        codes = codes == null ? List.of() : List.copyOf(codes);
    }
}
