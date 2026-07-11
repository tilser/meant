package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.port.CatalogDataUsePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shopify search facts/media default to session-only. Legal/product approval must explicitly enable
 * bounded persistence; source URLs may be rendered in-session but this policy never copies media.
 */
@Component
@RequiredArgsConstructor
public class ShopifyCatalogDataUsePolicy implements CatalogDataUsePolicy {
    private static final String POLICY_KEY = "shopify-global-catalog-v1";

    private final ShopifyGlobalCatalogProperties catalogProperties;
    private final ShopifyCatalogDataUseProperties dataUseProperties;

    @Override
    public boolean supports(DiscoverySourceIdentity source) {
        return source != null && source.equals(new DiscoverySourceIdentity(
                ShopifyGlobalCatalogNormalizer.SHOPIFY,
                ResultSourceType.PROVIDER_CATALOG,
                catalogProperties.sourceIdentity()
        ));
    }

    @Override
    public CatalogRetentionDecision decide(DiscoverySourceIdentity source, CatalogPayloadClass payloadClass) {
        return switch (payloadClass) {
            case SEARCH_FACTS, SEARCH_MEDIA -> dataUseProperties.searchPersistenceApproved()
                    ? CatalogRetentionDecision.bounded(POLICY_KEY, dataUseProperties.approvedSearchCacheTtl())
                    : CatalogRetentionDecision.sessionOnly(POLICY_KEY);
            case IDENTIFIERS_PROVENANCE, SAVED_INTERACTION ->
                    CatalogRetentionDecision.identifiersOnly(POLICY_KEY);
            case TRANSACTION_SNAPSHOT -> CatalogRetentionDecision.sessionOnly(POLICY_KEY);
        };
    }
}
