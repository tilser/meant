package com.meant.api.module.merchant.service.dto;

import java.util.UUID;

public record MerchantListItemResult(
        UUID id,
        String domain,
        String name,
        String description,
        String advertisedMcpEndpoint,
        String profileMcpEndpoint,
        boolean supportsIdentityLinking
) {
}
