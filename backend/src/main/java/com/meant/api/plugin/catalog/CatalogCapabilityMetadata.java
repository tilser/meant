package com.meant.api.plugin.catalog;

import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import java.net.URI;
import java.util.List;

final class CatalogCapabilityMetadata {

    static final String PROTOCOL_VERSION = "2026-04-08";
    static final String VERSION = "1.0.0";

    private CatalogCapabilityMetadata() {
    }

    static CapabilityAdvertisement required(CapabilityId id, String toolName) {
        return CapabilityAdvertisement.required(
                id,
                VERSION,
                List.of(toolName),
                PROTOCOL_VERSION,
                spec(id),
                schema(id)
        );
    }

    static CapabilityAdvertisement optional(CapabilityId id) {
        return new CapabilityAdvertisement(
                id,
                VERSION,
                List.of(),
                false,
                CapabilityAdvertisement.ProtocolVersions.exact(PROTOCOL_VERSION),
                CapabilityAdvertisement.Requirements.none(),
                spec(id),
                schema(id)
        );
    }

    private static URI spec(CapabilityId id) {
        return URI.create("https://ucp.dev/spec/" + id.value());
    }

    private static URI schema(CapabilityId id) {
        return URI.create("https://ucp.dev/schema/" + id.value() + ".json");
    }
}
