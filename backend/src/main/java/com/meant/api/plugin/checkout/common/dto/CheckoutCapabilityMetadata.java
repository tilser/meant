package com.meant.api.plugin.checkout.common.dto;

import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import java.net.URI;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

public final class CheckoutCapabilityMetadata {

    public static final String PROTOCOL_VERSION = "2026-04-08";
    public static final String VERSION = PROTOCOL_VERSION;
    public static final CapabilityId CHECKOUT = CapabilityId.of("dev.ucp.shopping.checkout");
    public static final CapabilityId CART = CapabilityId.of("dev.ucp.shopping.cart");

    private CheckoutCapabilityMetadata() {
    }

    public static CapabilityAdvertisement required(String toolName) {
        return CapabilityAdvertisement.required(
                CHECKOUT,
                VERSION,
                List.of(toolName),
                PROTOCOL_VERSION,
                spec(),
                schema()
        );
    }

    public static CapabilityAdvertisement optionalExtension(
            CapabilityId id,
            String specPath,
            String schemaName,
            List<CapabilityId> extendsCapabilities
    ) {
        return optionalExtension(id, specPath, schemaName, extendsCapabilities, JsonNodeFactory.instance.objectNode());
    }

    public static CapabilityAdvertisement optionalExtension(
            CapabilityId id,
            String specPath,
            String schemaName,
            List<CapabilityId> extendsCapabilities,
            JsonNode config
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

    private static URI spec() {
        return URI.create("https://ucp.dev/2026-04-08/specification/checkout");
    }

    private static URI schema() {
        return URI.create("https://ucp.dev/2026-04-08/schemas/shopping/checkout.json");
    }
}
