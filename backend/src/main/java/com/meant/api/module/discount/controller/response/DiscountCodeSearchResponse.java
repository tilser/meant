package com.meant.api.module.discount.controller.response;

import com.meant.api.module.discount.service.dto.DiscountCodeSearchResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DiscountCodeSearchResponse(
        UUID merchantId,
        String merchantDomain,
        boolean cached,
        Instant searchedAt,
        Instant expiresAt,
        List<DiscountCodeResponse> codes
) {

    public static DiscountCodeSearchResponse from(DiscountCodeSearchResult result) {
        return new DiscountCodeSearchResponse(
                result.merchantId(),
                result.merchantDomain(),
                result.cached(),
                result.searchedAt(),
                result.expiresAt(),
                result.codes().stream()
                        .map(DiscountCodeResponse::from)
                        .toList()
        );
    }
}
