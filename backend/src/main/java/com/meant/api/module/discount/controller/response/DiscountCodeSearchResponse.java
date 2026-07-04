package com.meant.api.module.discount.controller.response;

import com.meant.api.module.discount.service.dto.DiscountCodeSearchResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "DiscountCodeSearchResponse", description = "Discount code search and validation result.")
public record DiscountCodeSearchResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Local merchant UUID.", example = "00000000-0000-0000-0000-000000000001")
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Merchant storefront domain.", example = "merchant.example")
        String merchantDomain,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Whether this response was served from cache.")
        boolean cached,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Timestamp when the search was performed.")
        Instant searchedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Timestamp when this search result expires.")
        Instant expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Validated discount codes accepted by the merchant.")
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
