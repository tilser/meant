package com.meant.api.plugin.catalog.common.dto;

import java.util.UUID;
import java.util.Set;

/** Provider-neutral input shared by every federated catalog source. */
public record CatalogDiscoveryRequest(
        String query,
        UUID merchantId,
        int candidateLimit,
        CatalogSearchContext context,
        CatalogSearchSignals signals,
        CatalogSearchFilters filters,
        Set<ProviderIdentity> coveredProviders
) {

    public CatalogDiscoveryRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Catalog discovery query is required");
        }
        if (candidateLimit < 1) {
            throw new IllegalArgumentException("Catalog discovery candidate limit must be positive");
        }
        query = query.trim();
        coveredProviders = coveredProviders == null ? Set.of() : Set.copyOf(coveredProviders);
    }

    public CatalogDiscoveryRequest(
            String query,
            UUID merchantId,
            int candidateLimit,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters
    ) {
        this(query, merchantId, candidateLimit, context, signals, filters, Set.of());
    }

    public boolean broad() {
        return merchantId == null;
    }

    public CatalogDiscoveryRequest withCandidateLimit(int limit) {
        return new CatalogDiscoveryRequest(query, merchantId, limit, context, signals, filters, coveredProviders);
    }

    public CatalogDiscoveryRequest withCoveredProviders(Set<ProviderIdentity> providers) {
        return new CatalogDiscoveryRequest(query, merchantId, candidateLimit, context, signals, filters, providers);
    }
}
