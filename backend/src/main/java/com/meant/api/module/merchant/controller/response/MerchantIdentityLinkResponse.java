package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.constant.MerchantIdentityLinkStatus;
import com.meant.api.module.merchant.service.dto.MerchantIdentityLinkResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record MerchantIdentityLinkResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String merchantDomain,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String merchantName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MerchantIdentityLinkStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String scope,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
