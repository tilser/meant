package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record MerchantIntegrationResult(
        UUID id,
        UUID merchantId,
        String merchantName,
        MerchantIntegrationProvider provider,
        MerchantIntegrationKind kind,
        Set<MerchantIntegrationRole> roles,
        String externalMerchantId,
        String verifiedDomain,
        String verifiedShopIdentity,
        String endpoint,
        String protocolVersion,
        MerchantIntegrationAuthStrategy authStrategy,
        MerchantIntegrationStatus status,
        MerchantIntegrationSource source,
        Instant capturedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public MerchantIntegrationResult {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public MerchantIntegrationResult(
            UUID id,
            UUID merchantId,
            MerchantIntegrationProvider provider,
            MerchantIntegrationKind kind,
            Set<MerchantIntegrationRole> roles,
            String externalMerchantId,
            String verifiedDomain,
            String verifiedShopIdentity,
            String endpoint,
            String protocolVersion,
            MerchantIntegrationAuthStrategy authStrategy,
            MerchantIntegrationStatus status,
            MerchantIntegrationSource source,
            Instant capturedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                merchantId,
                null,
                provider,
                kind,
                roles,
                externalMerchantId,
                verifiedDomain,
                verifiedShopIdentity,
                endpoint,
                protocolVersion,
                authStrategy,
                status,
                source,
                capturedAt,
                createdAt,
                updatedAt
        );
    }
}
