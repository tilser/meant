package com.meant.api.module.catalog.service.port;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import java.util.List;

/** Adapter port implemented with the provider's existing lookup/get-product client. */
public interface CatalogProductRehydrationProvider {
    boolean supports(DiscoverySourceIdentity source);

    List<CatalogProductRehydrationResult> rehydrate(
            List<CatalogProductReference> references,
            CatalogRehydrationContext context
    );
}
