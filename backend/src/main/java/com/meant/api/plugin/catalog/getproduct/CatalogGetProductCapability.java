package com.meant.api.plugin.catalog.getproduct;

import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.plugin.catalog.common.dto.CatalogCapabilityMetadata;
import com.meant.api.plugin.catalog.common.exception.UcpCatalogResponseException;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductArguments;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductRequest;
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
public class CatalogGetProductCapability implements UcpCapability<CatalogGetProductRequest, ProductDetailsResponse> {

    public static final String TOOL_NAME = "get_product";
    public static final CapabilityId ID = CapabilityId.of("dev.ucp.shopping.catalog.get_product");

    private final ObjectMapper objectMapper;

    public CatalogGetProductCapability(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
    public CatalogGetProductArguments buildArguments(
            CatalogGetProductRequest request,
            NegotiatedCapabilities activeCapabilities
    ) {
        return new CatalogGetProductArguments(
                request.productId(),
                request.selected(),
                request.preferences(),
                request.context(),
                ShopifyCatalogExtensionArguments.extensions(activeCapabilities)
        );
    }

    @Override
    public ProductDetailsResponse parseResponse(UcpToolResponse response) {
        ProductDetailsResponse productDetailsResponse = CatalogPluginJson.parse(
                objectMapper,
                response,
                ProductDetailsResponse.class
        );
        if (productDetailsResponse == null || productDetailsResponse.product() == null) {
            throw new UcpCatalogResponseException("UCP get_product response did not contain product");
        }
        return productDetailsResponse;
    }
}
