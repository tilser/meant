package com.meant.api.module.catalog.service.port;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;

/** Provider-owned constraints for turning a verified catalog reference into a durable cart selection. */
public interface CatalogPurchaseReferencePolicy {
    boolean supports(DiscoverySourceIdentity source);

    boolean allows(CatalogProductReference reference);
}
