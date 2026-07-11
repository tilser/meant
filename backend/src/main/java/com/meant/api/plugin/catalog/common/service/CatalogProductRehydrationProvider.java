package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CatalogProductReference;
import com.meant.api.plugin.catalog.common.dto.CatalogProductRehydrationResult;
import com.meant.api.plugin.catalog.common.dto.CatalogRehydrationContext;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import java.util.List;

/** Adapter port implemented with the provider's existing lookup/get-product client. */
public interface CatalogProductRehydrationProvider {
    String metricsKey();

    boolean supports(DiscoverySourceIdentity source);

    List<CatalogProductRehydrationResult> rehydrate(
            List<CatalogProductReference> references,
            CatalogRehydrationContext context
    );
}
