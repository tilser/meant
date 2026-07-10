package com.meant.api.plugin.catalog.common.dto;

/** One provenance-bearing source event or one request-wide terminal event. */
public record CatalogDiscoveryEvent(
        CatalogDiscoveryEventType type,
        DiscoverySourceIdentity source,
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
        if ((type == CatalogDiscoveryEventType.SOURCE_DEGRADED) != (failure != null)) {
            throw new IllegalArgumentException("Only degraded source events carry a failure");
        }
    }

    public static CatalogDiscoveryEvent candidate(DiscoverySourceIdentity source, ProductCandidate candidate) {
        return new CatalogDiscoveryEvent(CatalogDiscoveryEventType.CANDIDATE, source, candidate, null, null);
    }

    public static CatalogDiscoveryEvent sourceComplete(DiscoverySourceIdentity source) {
        return new CatalogDiscoveryEvent(CatalogDiscoveryEventType.SOURCE_COMPLETE, source, null, null, null);
    }

    public static CatalogDiscoveryEvent sourceDegraded(
            DiscoverySourceIdentity source,
            CatalogSourceFailure failure
    ) {
        return new CatalogDiscoveryEvent(CatalogDiscoveryEventType.SOURCE_DEGRADED, source, null, failure, null);
    }

    public static CatalogDiscoveryEvent terminal(CatalogDiscoveryTerminalStatus status) {
        CatalogDiscoveryEventType type = status == CatalogDiscoveryTerminalStatus.FAILED
                ? CatalogDiscoveryEventType.ERROR
                : CatalogDiscoveryEventType.COMPLETE;
        return new CatalogDiscoveryEvent(type, null, null, null, status);
    }
}
