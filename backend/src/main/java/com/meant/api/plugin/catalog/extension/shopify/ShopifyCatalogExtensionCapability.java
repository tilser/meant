package com.meant.api.plugin.catalog.extension.shopify;

import com.meant.api.plugin.catalog.common.dto.CatalogCapabilityMetadata;
import com.meant.api.plugin.catalog.lookup.CatalogLookupCapability;
import com.meant.api.plugin.catalog.search.CatalogSearchCapability;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;

@Component
public class ShopifyCatalogExtensionCapability implements UcpCapability<Void, Void> {

    public static final CapabilityId ID = CapabilityId.of("dev.shopify.catalog");

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(new CapabilityAdvertisement(
                ID,
                CatalogCapabilityMetadata.PROTOCOL_VERSION,
                List.of(),
                false,
                CapabilityAdvertisement.ProtocolVersions.exact(CatalogCapabilityMetadata.PROTOCOL_VERSION),
                CapabilityAdvertisement.Requirements.none(),
                URI.create("https://shopify.dev/docs/agents/catalog/storefront-catalog"),
                URI.create("https://shopify.dev/ucp/schemas/2026-04-08/shopify_catalog.json"),
                List.of(CatalogLookupCapability.ID, CatalogSearchCapability.ID),
                JsonNodeFactory.instance.objectNode()
        ));
    }

    @Override
    public Object buildArguments(Void request, NegotiatedCapabilities activeCapabilities) {
        return null;
    }

    @Override
    public Void parseResponse(UcpToolResponse response) {
        return null;
    }
}
