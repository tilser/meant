package com.meant.api.plugin.catalog.common.dto;

import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import java.net.URI;
import java.util.List;

public final class CatalogCapabilityMetadata {

    public static final String PROTOCOL_VERSION = "2026-04-08";
    public static final String VERSION = PROTOCOL_VERSION;

    private CatalogCapabilityMetadata() {
    }

    public static CapabilityAdvertisement required(CapabilityId id, String toolName) {
        return CapabilityAdvertisement.required(
                id,
                VERSION,
                List.of(toolName),
                PROTOCOL_VERSION,
                spec(id),
                schema(id)
        );
    }

    private static URI spec(CapabilityId id) {
        return switch (id.value()) {
            case "dev.ucp.shopping.catalog.search" -> URI.create(
                    "https://ucp.dev/2026-04-08/specification/catalog/search"
            );
            case "dev.ucp.shopping.catalog.lookup" -> URI.create(
                    "https://ucp.dev/2026-04-08/specification/catalog/lookup"
            );
            default -> throw unsupportedProfileCapability(id);
        };
    }

    private static URI schema(CapabilityId id) {
        return switch (id.value()) {
            case "dev.ucp.shopping.catalog.search" -> URI.create(
                    "https://ucp.dev/2026-04-08/schemas/shopping/catalog_search.json"
            );
            case "dev.ucp.shopping.catalog.lookup" -> URI.create(
                    "https://ucp.dev/2026-04-08/schemas/shopping/catalog_lookup.json"
            );
            default -> throw unsupportedProfileCapability(id);
        };
    }

    private static IllegalArgumentException unsupportedProfileCapability(CapabilityId id) {
        return new IllegalArgumentException("Unsupported UCP catalog profile capability: " + id.value());
    }
}
