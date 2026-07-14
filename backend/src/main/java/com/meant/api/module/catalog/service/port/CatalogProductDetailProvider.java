package com.meant.api.module.catalog.service.port;

import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;

/** Adapter port for a current, non-persisted full product-detail projection. */
public interface CatalogProductDetailProvider {
    boolean supportsDetails(DiscoverySourceIdentity source);

    CatalogProductDetailResult getDetails(
            CatalogProductReference reference,
            CatalogRehydrationContext context
    );
}
