package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryEventType;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailure;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import java.util.List;

public record UserFederatedProductSearchStreamEvent(
        CatalogDiscoveryEventType type,
        DiscoverySourceIdentity source,
        List<DiscoverySourceIdentity> observationSources,
        CanonicalProduct candidate,
        CatalogSourceFailure failure,
        CatalogDiscoveryTerminalStatus terminalStatus
) {
}
