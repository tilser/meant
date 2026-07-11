package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.CatalogPayloadClass;
import com.meant.api.plugin.catalog.common.dto.CatalogRetentionDecision;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;

/** Provider extension point for reviewed retention rules; unknown sources are handled by the resolver. */
public interface CatalogDataUsePolicy {
    boolean supports(DiscoverySourceIdentity source);

    CatalogRetentionDecision decide(DiscoverySourceIdentity source, CatalogPayloadClass payloadClass);
}
