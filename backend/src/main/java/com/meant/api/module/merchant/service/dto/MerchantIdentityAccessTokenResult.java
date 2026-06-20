package com.meant.api.module.merchant.service.dto;

import java.time.Instant;

public record MerchantIdentityAccessTokenResult(
        String accessToken,
        String tokenType,
        String scope,
        Instant expiresAt
) {
}
