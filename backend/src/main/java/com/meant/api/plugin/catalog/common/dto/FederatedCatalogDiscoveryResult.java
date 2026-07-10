package com.meant.api.plugin.catalog.common.dto;

import java.util.List;

/** Deterministically ordered results from all eligible discovery sources. */
public record FederatedCatalogDiscoveryResult(
        CatalogDiscoveryTerminalStatus status,
        List<CatalogSourceResult> sources,
        List<ProductCandidate> candidates,
        boolean truncated
) {

    public FederatedCatalogDiscoveryResult {
        if (status == null) {
            throw new IllegalArgumentException("Catalog discovery terminal status is required");
        }
        sources = sources == null ? List.of() : List.copyOf(sources);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (status == CatalogDiscoveryTerminalStatus.FAILED && !candidates.isEmpty()) {
            throw new IllegalArgumentException("Failed catalog discovery cannot contain candidates");
        }
        if (status == CatalogDiscoveryTerminalStatus.FAILED && truncated) {
            throw new IllegalArgumentException("Failed catalog discovery cannot advertise a result continuation");
        }
    }
}
