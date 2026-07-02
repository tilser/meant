package com.meant.api.plugin.checkout.common.dto;

import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import java.net.URI;
import java.util.List;
import java.util.Map;

public final class CheckoutCapabilityMetadata {

    public static final String PROTOCOL_VERSION = "2026-04-08";
    public static final String VERSION = "1.0.0";
    public static final CapabilityId CHECKOUT = CapabilityId.of("dev.ucp.shopping.checkout");
    public static final CapabilityId CART = CapabilityId.of("dev.ucp.shopping.cart");

    private CheckoutCapabilityMetadata() {
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

    public static CapabilityAdvertisement optionalExtension(
            CapabilityId id,
            String specPath,
            String schemaName,
            List<CapabilityId> extendsCapabilities
    ) {
        return optionalExtension(id, specPath, schemaName, extendsCapabilities, Map.of());
    }

    public static CapabilityAdvertisement optionalExtension(
            CapabilityId id,
            String specPath,
            String schemaName,
            List<CapabilityId> extendsCapabilities,
            Map<String, Object> config
    ) {
        return new CapabilityAdvertisement(
                id,
                VERSION,
                List.of(),
                false,
                CapabilityAdvertisement.ProtocolVersions.exact(PROTOCOL_VERSION),
                CapabilityAdvertisement.Requirements.none(),
                URI.create("https://ucp.dev/" + PROTOCOL_VERSION + "/specification/" + specPath),
                URI.create("https://ucp.dev/" + PROTOCOL_VERSION + "/schemas/shopping/" + schemaName),
                extendsCapabilities,
                config
        );
    }

    private static URI spec(CapabilityId id) {
        return URI.create("https://ucp.dev/spec/" + id.value());
    }

    private static URI schema(CapabilityId id) {
        return URI.create("https://ucp.dev/schema/" + id.value() + ".json");
    }
}
