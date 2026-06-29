package com.meant.api.plugin.catalog.shopify;

import com.meant.api.plugin.catalog.common.dto.CatalogCapabilityMetadata;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ShopifyCatalogExtensionCapability implements UcpCapability<Void, Void> {

    public static final CapabilityId ID = CapabilityId.of("dev.shopify.catalog");

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(CatalogCapabilityMetadata.optional(ID));
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
