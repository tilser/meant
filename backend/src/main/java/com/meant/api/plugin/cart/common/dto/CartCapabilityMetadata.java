package com.meant.api.plugin.cart.common.dto;

import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import java.net.URI;
import java.util.List;

public final class CartCapabilityMetadata {

    public static final String PROTOCOL_VERSION = "2026-04-08";
    public static final String VERSION = "1.0.0";

    private CartCapabilityMetadata() {
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
        return URI.create("https://ucp.dev/spec/" + id.value());
    }

    private static URI schema(CapabilityId id) {
        return URI.create("https://ucp.dev/schema/" + id.value() + ".json");
    }
}
