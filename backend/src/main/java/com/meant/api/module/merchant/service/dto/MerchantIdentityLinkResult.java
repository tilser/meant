package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.MerchantIdentityLinkStatus;
import com.meant.api.module.merchant.entity.MerchantIdentityLink;
import java.time.Instant;
import java.util.UUID;

public record MerchantIdentityLinkResult(
        UUID merchantId,
        String merchantDomain,
        String merchantName,
        MerchantIdentityLinkStatus status,
        String scope,
        Instant expiresAt,
        Instant updatedAt
) {

    public static MerchantIdentityLinkResult from(MerchantIdentityLink link) {
        return new MerchantIdentityLinkResult(
                link.getMerchant().getId(),
                link.getMerchant().getDomain(),
                link.getMerchant().getName(),
                link.getStatus(),
                link.getScope(),
                link.getExpiresAt(),
                link.getUpdatedAt()
        );
    }
}
