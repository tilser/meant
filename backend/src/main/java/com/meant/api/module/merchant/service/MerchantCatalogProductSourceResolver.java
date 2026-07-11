package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByMerchantsQuery;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Resolves legacy merchant results to server-owned source identities in one integration lookup. */
@Component
public class MerchantCatalogProductSourceResolver {
    public static final DiscoverySourceIdentity UNRESOLVED_SOURCE = new DiscoverySourceIdentity(
            new ProviderIdentity("UNRESOLVED_MERCHANT_SOURCE"),
            ResultSourceType.MERCHANT_STOREFRONT,
            "UNRESOLVED"
    );

    private final MerchantIntegrationLookupService integrationLookupService;

    public MerchantCatalogProductSourceResolver(MerchantIntegrationLookupService integrationLookupService) {
        this.integrationLookupService = integrationLookupService;
    }

    public Map<UUID, DiscoverySourceIdentity> resolve(List<MerchantSemanticProductResult> products) {
        Set<UUID> merchantIds = products.stream()
                .map(MerchantSemanticProductResult::merchantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (merchantIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<MerchantIntegrationResult>> integrations = integrationLookupService.listByMerchants(
                        new ListMerchantIntegrationsByMerchantsQuery(merchantIds)
                ).stream()
                .filter(this::eligible)
                .collect(Collectors.groupingBy(MerchantIntegrationResult::merchantId));
        Map<UUID, DiscoverySourceIdentity> sources = new LinkedHashMap<>();
        for (UUID merchantId : merchantIds) {
            List<String> endpoints = products.stream()
                    .filter(product -> merchantId.equals(product.merchantId()))
                    .map(MerchantSemanticProductResult::endpoint)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            List<MerchantIntegrationResult> matches = integrations.getOrDefault(merchantId, List.of()).stream()
                    .filter(integration -> endpoints.size() == 1 && endpoints.contains(integration.endpoint()))
                    .toList();
            sources.put(merchantId, matches.size() == 1 ? source(matches.getFirst()) : UNRESOLVED_SOURCE);
        }
        return Map.copyOf(sources);
    }

    private boolean eligible(MerchantIntegrationResult integration) {
        return integration.status() == MerchantIntegrationStatus.ACTIVE
                && (integration.roles().contains(MerchantIntegrationRole.STOREFRONT_CATALOG)
                        || integration.roles().contains(MerchantIntegrationRole.CATALOG_PROVENANCE));
    }

    private DiscoverySourceIdentity source(MerchantIntegrationResult integration) {
        if (integration.provider() == null) {
            return UNRESOLVED_SOURCE;
        }
        if (integration.provider() == MerchantIntegrationProvider.GENERIC_UCP) {
            return MerchantCatalogSourceIdentity.DISCOVERY_SOURCE;
        }
        return new DiscoverySourceIdentity(
                new ProviderIdentity(integration.provider().name()),
                ResultSourceType.MERCHANT_STOREFRONT,
                "MERCHANT_INTEGRATION:" + integration.id()
        );
    }
}
