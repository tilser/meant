package com.meant.api.plugin.catalog.extension.shopify;

import com.meant.api.plugin.catalog.lookup.CatalogLookupCapability;
import com.meant.api.plugin.catalog.search.CatalogSearchCapability;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.net.URI;
import java.util.List;
import tools.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;

@Component
public class ShopifyGlobalCatalogExtensionCapability implements UcpCapability<Void, Void> {

    public static final CapabilityId ID = CapabilityId.of("dev.shopify.catalog.global");
    private static final URI SPEC = URI.create("https://shopify.dev/docs/agents/catalog/global-catalog");

    private final ShopifyGlobalCatalogExtensionProperties properties;

    public ShopifyGlobalCatalogExtensionCapability(ShopifyGlobalCatalogExtensionProperties properties) {
        this.properties = properties;
    }

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(new CapabilityAdvertisement(
                id(),
                properties.protocolVersion(),
                List.of(),
                false,
                CapabilityAdvertisement.ProtocolVersions.exact(properties.protocolVersion()),
                CapabilityAdvertisement.Requirements.none(),
                SPEC,
                URI.create("https://shopify.dev/ucp/schemas/%s/shopify_catalog_global.json"
                        .formatted(properties.protocolVersion())),
                List.of(
                        CatalogSearchCapability.ID,
                        CatalogLookupCapability.ID
                ),
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
