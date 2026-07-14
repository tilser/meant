package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.properties.GenericUcpCatalogDataUseProperties;
import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.port.CatalogDataUsePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reviewed policy for server-owned direct generic UCP storefront observations. Search uses one
 * aggregate source identity, while offer provenance identifies the concrete merchant storefront;
 * both belong to the same reviewed provider/type policy family.
 */
@Component
@RequiredArgsConstructor
public class GenericUcpCatalogDataUsePolicy implements CatalogDataUsePolicy {
    private static final String POLICY_KEY = "generic-ucp-storefront-v2";

    private final GenericUcpCatalogDataUseProperties properties;

    @Override
    public boolean supports(DiscoverySourceIdentity source) {
        return source != null
                && MerchantCatalogSourceIdentity.PROVIDER.equals(source.provider())
                && source.type() == ResultSourceType.MERCHANT_STOREFRONT;
    }

    @Override
    public CatalogRetentionDecision decide(DiscoverySourceIdentity source, CatalogPayloadClass payloadClass) {
        return switch (payloadClass) {
            case SEARCH_FACTS, SEARCH_MEDIA ->
                    CatalogRetentionDecision.bounded(POLICY_KEY, properties.searchCacheTtl());
            case IDENTIFIERS_PROVENANCE, SAVED_INTERACTION ->
                    CatalogRetentionDecision.identifiersOnly(POLICY_KEY);
            case TRANSACTION_SNAPSHOT -> CatalogRetentionDecision.sessionOnly(POLICY_KEY);
        };
    }
}
