package com.meant.api.plugin.catalog.common.dto;

import java.util.List;

/**
 * One source event or request terminal. {@code source} is the acquisition path that emitted the event;
 * candidate provenance retains the provider observation sources that supplied the product facts.
 */
public record CatalogDiscoveryEvent(
        CatalogDiscoveryEventType type,
        DiscoverySourceIdentity source,
        List<DiscoverySourceIdentity> observationSources,
        ProductCandidate candidate,
        CatalogSourceFailure failure,
        CatalogDiscoveryTerminalStatus terminalStatus
) {

    public CatalogDiscoveryEvent {
        if (type == null) {
            throw new IllegalArgumentException("Catalog discovery event type is required");
        }
        boolean terminal = type == CatalogDiscoveryEventType.COMPLETE || type == CatalogDiscoveryEventType.ERROR;
        if (terminal != (terminalStatus != null)) {
            throw new IllegalArgumentException("Only terminal catalog discovery events need terminal status");
        }
        if (!terminal && source == null) {
            throw new IllegalArgumentException("Source catalog discovery events need source identity");
        }
        if ((type == CatalogDiscoveryEventType.CANDIDATE) != (candidate != null)) {
            throw new IllegalArgumentException("Only candidate events carry a product candidate");
        }
        observationSources = observationSources == null ? List.of() : List.copyOf(observationSources);
        if (type == CatalogDiscoveryEventType.CANDIDATE && observationSources.isEmpty()) {
            throw new IllegalArgumentException("Candidate events need observation source provenance");
        }
        if (type != CatalogDiscoveryEventType.CANDIDATE && !observationSources.isEmpty()) {
            throw new IllegalArgumentException("Only candidate events carry observation sources");
        }
        if ((type == CatalogDiscoveryEventType.SOURCE_DEGRADED) != (failure != null)) {
            throw new IllegalArgumentException("Only degraded source events carry a failure");
        }
    }

    public static CatalogDiscoveryEvent candidate(DiscoverySourceIdentity source, ProductCandidate candidate) {
        List<DiscoverySourceIdentity> observationSources = candidate.provenance().stream()
                .map(ResultProvenance::discoverySource)
                .distinct()
                .toList();
        return new CatalogDiscoveryEvent(
                CatalogDiscoveryEventType.CANDIDATE,
                source,
                observationSources,
                candidate,
                null,
                null
        );
    }

    public static CatalogDiscoveryEvent sourceComplete(DiscoverySourceIdentity source) {
        return new CatalogDiscoveryEvent(CatalogDiscoveryEventType.SOURCE_COMPLETE, source, List.of(), null, null, null);
    }

    public static CatalogDiscoveryEvent sourceDegraded(
            DiscoverySourceIdentity source,
            CatalogSourceFailure failure
    ) {
        return new CatalogDiscoveryEvent(
                CatalogDiscoveryEventType.SOURCE_DEGRADED,
                source,
                List.of(),
                null,
                failure,
                null
        );
    }

    public static CatalogDiscoveryEvent terminal(CatalogDiscoveryTerminalStatus status) {
        CatalogDiscoveryEventType type = status == CatalogDiscoveryTerminalStatus.FAILED
                ? CatalogDiscoveryEventType.ERROR
                : CatalogDiscoveryEventType.COMPLETE;
        return new CatalogDiscoveryEvent(type, null, List.of(), null, null, status);
    }
}
