package com.meant.api.module.catalog.service.dto;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;

import java.util.Set;
import java.util.UUID;

/** Provider-neutral input shared by every federated catalog source. */
public record CatalogDiscoveryRequest(
        String query,
        UUID merchantId,
        int candidateLimit,
        CatalogSearchContext context,
        CatalogSearchSignals signals,
        CatalogSearchFilters filters,
        CatalogDiscoveryFilters discoveryFilters,
        CatalogSimilarityReference similarityReference,
        Set<ProviderIdentity> coveredProviders
) {

    public CatalogDiscoveryRequest {
        query = query == null || query.isBlank() ? null : query.trim();
        if (query == null && similarityReference == null) {
            throw new IllegalArgumentException("Catalog discovery query or similarity reference is required");
        }
        if (candidateLimit < 1) {
            throw new IllegalArgumentException("Catalog discovery candidate limit must be positive");
        }
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
        this(query, merchantId, candidateLimit, context, signals, filters, null, null, Set.of());
    }

    public CatalogDiscoveryRequest(
            String query,
            UUID merchantId,
            int candidateLimit,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters,
            CatalogDiscoveryFilters discoveryFilters
    ) {
        this(query, merchantId, candidateLimit, context, signals, filters, discoveryFilters, null, Set.of());
    }

    public CatalogDiscoveryRequest(
            String query,
            UUID merchantId,
            int candidateLimit,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters,
            CatalogSimilarityReference similarityReference
    ) {
        this(query, merchantId, candidateLimit, context, signals, filters, null, similarityReference, Set.of());
    }

    public CatalogDiscoveryRequest(
            String query,
            UUID merchantId,
            int candidateLimit,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters,
            Set<ProviderIdentity> coveredProviders
    ) {
        this(query, merchantId, candidateLimit, context, signals, filters, null, null, coveredProviders);
    }

    public CatalogDiscoveryRequest(
            String query,
            UUID merchantId,
            int candidateLimit,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters,
            CatalogDiscoveryFilters discoveryFilters,
            Set<ProviderIdentity> coveredProviders
    ) {
        this(query, merchantId, candidateLimit, context, signals, filters, discoveryFilters, null, coveredProviders);
    }

    public CatalogDiscoveryRequest(
            String query,
            UUID merchantId,
            int candidateLimit,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters,
            CatalogSimilarityReference similarityReference,
            Set<ProviderIdentity> coveredProviders
    ) {
        this(query, merchantId, candidateLimit, context, signals, filters, null, similarityReference, coveredProviders);
    }

    public boolean broad() {
        return merchantId == null;
    }

    public CatalogDiscoveryRequest withCandidateLimit(int limit) {
        return new CatalogDiscoveryRequest(
                query, merchantId, limit, context, signals, filters,
                discoveryFilters, similarityReference, coveredProviders);
    }

    public CatalogDiscoveryRequest withCoveredProviders(Set<ProviderIdentity> providers) {
        return new CatalogDiscoveryRequest(
                query, merchantId, candidateLimit, context, signals, filters,
                discoveryFilters, similarityReference, providers);
    }
}
