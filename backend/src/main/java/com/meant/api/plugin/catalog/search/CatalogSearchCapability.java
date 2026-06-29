package com.meant.api.plugin.catalog.search;

import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.plugin.catalog.common.dto.CatalogCapabilityMetadata;
import com.meant.api.plugin.catalog.search.dto.CatalogSearchArguments;
import com.meant.api.plugin.catalog.search.dto.CatalogSearchRequest;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyCatalogExtensionArguments;
import com.meant.api.plugin.catalog.common.support.CatalogPluginJson;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogSearchCapability implements UcpCapability<CatalogSearchRequest, CatalogSearchResponse> {

    public static final String TOOL_NAME = "search_catalog";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.catalog.search");

    private final ObjectMapper objectMapper;

    public CatalogSearchCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CatalogSearchCapability() {
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
    public CatalogSearchArguments buildArguments(
            CatalogSearchRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new CatalogSearchArguments(
                new CatalogSearchArguments.Catalog(
                        request.query(),
                        request.context(),
                        request.signals(),
                        request.filters(),
                        new CatalogSearchArguments.Pagination(null, request.limit())
                ),
                ShopifyCatalogExtensionArguments.extensions(activeCapabilities)
        );
    }

    @Override
    public CatalogSearchResponse parseResponse(UcpToolResponse response) {
        CatalogSearchResponse catalogSearchResponse = CatalogPluginJson.parse(
                objectMapper,
                response,
                CatalogSearchResponse.class
        );
        if (catalogSearchResponse == null || catalogSearchResponse.products() == null) {
            return new CatalogSearchResponse(List.of(), null);
        }
        return catalogSearchResponse;
    }
}
