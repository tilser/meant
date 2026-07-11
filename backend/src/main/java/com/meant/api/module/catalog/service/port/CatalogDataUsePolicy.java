package com.meant.api.module.catalog.service.port;

import com.meant.api.module.catalog.service.dto.CatalogPayloadClass;
import com.meant.api.module.catalog.service.dto.CatalogRetentionDecision;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;

/** Provider extension point for reviewed retention rules; unknown sources are handled by the resolver. */
public interface CatalogDataUsePolicy {
    boolean supports(DiscoverySourceIdentity source);

    CatalogRetentionDecision decide(DiscoverySourceIdentity source, CatalogPayloadClass payloadClass);
}
