package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.constant.MerchantIdentityLinkStatus;
import com.meant.api.module.merchant.service.dto.MerchantIdentityLinkResult;
import java.time.Instant;
import java.util.UUID;

public record MerchantIdentityLinkResponse(
        UUID merchantId,
        String merchantDomain,
        String merchantName,
        MerchantIdentityLinkStatus status,
        String scope,
        Instant expiresAt,
        Instant updatedAt
) {

    public static MerchantIdentityLinkResponse from(MerchantIdentityLinkResult result) {
        return new MerchantIdentityLinkResponse(
                result.merchantId(),
                result.merchantDomain(),
                result.merchantName(),
                result.status(),
                result.scope(),
                result.expiresAt(),
                result.updatedAt()
        );
    }
}
