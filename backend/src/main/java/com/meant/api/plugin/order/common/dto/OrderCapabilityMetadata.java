package com.meant.api.plugin.order.common.dto;

import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import java.net.URI;
import java.util.List;

public final class OrderCapabilityMetadata {

    public static final String PROTOCOL_VERSION = "2026-04-08";
    public static final String VERSION = PROTOCOL_VERSION;
    public static final CapabilityId ORDER = CapabilityId.of("dev.ucp.shopping.order");

    private OrderCapabilityMetadata() {
    }

    public static CapabilityAdvertisement required(String toolName) {
        return CapabilityAdvertisement.required(
                ORDER,
                VERSION,
                List.of(toolName),
                PROTOCOL_VERSION,
                spec(),
                schema()
        );
    }

    private static URI spec() {
        return URI.create("https://ucp.dev/2026-04-08/specification/order");
    }

    private static URI schema() {
        return URI.create("https://ucp.dev/2026-04-08/schemas/shopping/order.json");
    }
}
