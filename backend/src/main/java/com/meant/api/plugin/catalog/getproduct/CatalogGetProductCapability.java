package com.meant.api.plugin.catalog.getproduct;

import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.plugin.catalog.common.dto.CatalogCapabilityMetadata;
import com.meant.api.plugin.catalog.common.exception.UcpCatalogResponseException;
import com.meant.api.plugin.catalog.common.support.CatalogPluginJson;
import com.meant.api.plugin.catalog.extension.CatalogExtensionRegistry;
import com.meant.api.plugin.catalog.extension.CatalogTool;
import com.meant.api.plugin.catalog.lookup.CatalogLookupCapability;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductArguments;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductRequest;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductResponse;
import com.meant.api.plugin.spi.CapabilityAdvertisement;
import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogGetProductCapability implements UcpCapability<CatalogGetProductRequest, ProductDetailsResponse> {

    public static final String TOOL_NAME = "get_product";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.catalog.get_product");

    private final ObjectMapper objectMapper;
    private final CatalogExtensionRegistry extensionRegistry;

    public CatalogGetProductCapability(ObjectMapper objectMapper, CatalogExtensionRegistry extensionRegistry) {
        this.objectMapper = objectMapper;
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public CapabilityId id() {
        return ID;
    }

    @Override
    public List<CapabilityAdvertisement> advertisements() {
        return List.of(CatalogCapabilityMetadata.required(CatalogLookupCapability.ID, TOOL_NAME));
    }

    @Override
    public CatalogGetProductArguments buildArguments(
            CatalogGetProductRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new CatalogGetProductArguments(
                new CatalogGetProductArguments.Catalog(
                        request.productId(),
                        request.selected(),
                        request.preferences(),
                        request.context(),
                        request.filters()
                ),
                extensionRegistry.extensions(CatalogTool.GET_PRODUCT, activeCapabilities)
        );
    }

    @Override
    public ProductDetailsResponse parseResponse(UcpToolResponse response) {
        CatalogGetProductResponse catalogGetProductResponse = CatalogPluginJson.parse(
                objectMapper,
                response,
                CatalogGetProductResponse.class
        );
        ProductDetailsResponse productDetailsResponse = catalogGetProductResponse == null
                ? null
                : catalogGetProductResponse.toProductDetailsResponse();
        if (productDetailsResponse == null || productDetailsResponse.product() == null) {
            throw new UcpCatalogResponseException("UCP get_product response did not contain product");
        }
        return productDetailsResponse;
    }
}
