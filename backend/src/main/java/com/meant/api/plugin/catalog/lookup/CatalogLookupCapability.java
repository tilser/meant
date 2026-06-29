package com.meant.api.plugin.catalog.lookup;

import com.meant.api.plugin.catalog.dto.CatalogCapabilityMetadata;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupArguments;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupRequest;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupResponse;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyCatalogExtensionArguments;
import com.meant.api.plugin.catalog.support.CatalogPluginJson;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogLookupCapability implements UcpCapability<CatalogLookupRequest, CatalogLookupResponse> {

    public static final String TOOL_NAME = "lookup_catalog";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.catalog.lookup");

    private final ObjectMapper objectMapper;

    public CatalogLookupCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CatalogLookupCapability() {
        this(new ObjectMapper());
    }

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(CatalogCapabilityMetadata.required(ID, TOOL_NAME));
    }

    @Override
    public CatalogLookupArguments buildArguments(
            CatalogLookupRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new CatalogLookupArguments(
                List.of(request.productId()),
                request.context(),
                ShopifyCatalogExtensionArguments.extensions(activeCapabilities)
        );
    }

    @Override
    public CatalogLookupResponse parseResponse(UcpToolResponse response) {
        CatalogLookupResponse catalogLookupResponse = CatalogPluginJson.parse(
                objectMapper,
                response,
                CatalogLookupResponse.class
        );
        return catalogLookupResponse == null ? new CatalogLookupResponse(null, null, List.of()) : catalogLookupResponse;
    }
}
