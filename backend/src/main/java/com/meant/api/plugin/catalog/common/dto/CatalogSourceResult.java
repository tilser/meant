package com.meant.api.plugin.catalog.common.dto;

import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.List;

/** Provider-neutral output from one catalog source invocation. */
public record CatalogSourceResult(
        ProviderIdentity provider,
        DiscoverySourceIdentity discoverySource,
        CatalogSourceOperation operation,
        String protocolVersion,
        NegotiatedCapabilities negotiatedCapabilities,
        List<ProductCandidate> candidates,
        List<CatalogSourceMessage> messages,
        CatalogSourcePage page,
        boolean truncated,
        CatalogSourceFailure failure
) {

    public CatalogSourceResult {
        if (provider == null || discoverySource == null || operation == null) {
            throw new IllegalArgumentException("Catalog source identity and operation are required");
        }
        if (!provider.equals(discoverySource.provider())) {
            throw new IllegalArgumentException("Catalog source provider must match discovery source provider");
        }
        protocolVersion = protocolVersion == null || protocolVersion.isBlank() ? null : protocolVersion.trim();
        negotiatedCapabilities = negotiatedCapabilities == null
                ? NegotiatedCapabilities.none()
                : negotiatedCapabilities;
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        messages = messages == null ? List.of() : List.copyOf(messages);
        if (failure != null && !candidates.isEmpty()) {
            throw new IllegalArgumentException("A failed source result must not expose partial unvalidated candidates");
        }
    }

    public boolean successful() {
        return failure == null;
    }
}
