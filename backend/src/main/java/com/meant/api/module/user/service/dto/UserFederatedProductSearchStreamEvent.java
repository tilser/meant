package com.meant.api.module.user.service.dto;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryEventType;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.plugin.catalog.common.dto.CatalogSourceFailure;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;

public record UserFederatedProductSearchStreamEvent(
        CatalogDiscoveryEventType type,
        DiscoverySourceIdentity source,
        CanonicalProduct candidate,
        CatalogSourceFailure failure,
        CatalogDiscoveryTerminalStatus terminalStatus
) {
}
