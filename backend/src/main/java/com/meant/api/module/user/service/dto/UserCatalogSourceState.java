package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogSourceFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogSourceOperation;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import java.time.Duration;

/** Safe public state for one source invocation; upstream messages and endpoints are deliberately excluded. */
public record UserCatalogSourceState(
        DiscoverySourceIdentity source,
        CatalogSourceOperation operation,
        boolean degraded,
        boolean truncated,
        CatalogSourceFailureKind failureKind,
        CatalogRehydrationFailureKind rehydrationFailureKind,
        Duration retryAfter
) {
}
