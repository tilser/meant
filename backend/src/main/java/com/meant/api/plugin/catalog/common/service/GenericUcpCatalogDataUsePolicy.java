package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CatalogPayloadClass;
import com.meant.api.plugin.catalog.common.dto.CatalogRetentionDecision;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reviewed policy for direct generic UCP merchant storefront observations. */
@Component
@RequiredArgsConstructor
public class GenericUcpCatalogDataUsePolicy implements CatalogDataUsePolicy {
    public static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            new ProviderIdentity("GENERIC_UCP"), ResultSourceType.MERCHANT_STOREFRONT, "MEANT_MERCHANT_SEMANTIC");
    private static final String POLICY_KEY = "generic-ucp-storefront-v1";

    private final GenericUcpCatalogDataUseProperties properties;

    @Override
    public boolean supports(DiscoverySourceIdentity source) {
        return SOURCE.equals(source);
    }

    @Override
    public CatalogRetentionDecision decide(DiscoverySourceIdentity source, CatalogPayloadClass payloadClass) {
        return switch (payloadClass) {
            case SEARCH_FACTS, SEARCH_MEDIA ->
                    CatalogRetentionDecision.bounded(POLICY_KEY, properties.searchCacheTtl());
            case IDENTIFIERS_PROVENANCE, SAVED_INTERACTION ->
                    CatalogRetentionDecision.identifiersOnly(POLICY_KEY);
            case TRANSACTION_SNAPSHOT ->
                    CatalogRetentionDecision.bounded(POLICY_KEY, properties.transactionSnapshotTtl());
        };
    }
}
