package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByMerchantsQuery;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Excludes merchant storefronts already covered by a scheduled provider-wide source. */
@Service
@RequiredArgsConstructor
public class MerchantCatalogDiscoveryEligibilityPolicy {

    private final MerchantIntegrationLookupService merchantIntegrationLookupService;

    public List<MerchantSemanticSearchResult> eligible(
            List<MerchantSemanticSearchResult> merchants,
            Set<ProviderIdentity> coveredProviders,
            UUID scopedMerchantId
    ) {
        if (scopedMerchantId != null || merchants.isEmpty() || coveredProviders.isEmpty()) {
            return merchants;
        }
        Set<UUID> merchantIds = merchants.stream()
                .map(MerchantSemanticSearchResult::merchantId)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> coveredMerchantIds = merchantIntegrationLookupService.listByMerchants(
                        new ListMerchantIntegrationsByMerchantsQuery(merchantIds)
                ).stream()
                .filter(this::activeCatalogIntegration)
                .filter(integration -> coveredProviders.contains(
                        new ProviderIdentity(integration.provider().name())
                ))
                .map(MerchantIntegrationResult::merchantId)
                .collect(Collectors.toUnmodifiableSet());
        return merchants.stream()
                .filter(merchant -> !coveredMerchantIds.contains(merchant.merchantId()))
                .toList();
    }

    private boolean activeCatalogIntegration(MerchantIntegrationResult integration) {
        return integration.status() == MerchantIntegrationStatus.ACTIVE
                && (integration.roles().contains(MerchantIntegrationRole.CATALOG_PROVENANCE)
                        || integration.roles().contains(MerchantIntegrationRole.STOREFRONT_CATALOG));
    }
}
