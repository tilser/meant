package com.meant.api.plugin.catalog.shopify;

import com.meant.api.plugin.catalog.lookup.CatalogLookupCapability;
import com.meant.api.plugin.catalog.search.CatalogSearchCapability;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ShopifyGlobalCatalogExtensionCapability implements UcpCapability<Void, Void> {

    private final ShopifyGlobalCatalogProperties properties;

    public ShopifyGlobalCatalogExtensionCapability(ShopifyGlobalCatalogProperties properties) {
        this.properties = properties;
    }

    @Override
    public CapabilityId id() {
        return CapabilityId.of(properties.extensionId());
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(new CapabilityAdvertisement(
                id(),
                properties.extensionVersion(),
                List.of(),
                false,
                CapabilityAdvertisement.ProtocolVersions.exact(properties.protocolVersion()),
                CapabilityAdvertisement.Requirements.none(),
                properties.extensionSpec(),
                properties.extensionSchema(),
                List.of(CatalogSearchCapability.ID, CatalogLookupCapability.ID),
                java.util.Map.of()
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
