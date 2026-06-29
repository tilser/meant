package com.meant.api.plugin.catalog.common.service;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.exception.MerchantProductDetailsException;
import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.dto.CatalogLookupResult;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.CatalogSearchSignals;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.plugin.catalog.getproduct.CatalogGetProductCapability;
import com.meant.api.plugin.catalog.getproduct.dto.CatalogGetProductRequest;
import com.meant.api.plugin.catalog.lookup.CatalogLookupCapability;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupRequest;
import com.meant.api.plugin.catalog.lookup.dto.CatalogLookupResponse;
import com.meant.api.plugin.catalog.search.CatalogSearchCapability;
import com.meant.api.plugin.catalog.search.dto.CatalogSearchRequest;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpCapability;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantCatalogPluginDispatchService {

    private final MerchantMcpToolClient merchantMcpToolClient;
    private final CapabilityRegistry capabilityRegistry;

    public CatalogSearchResult searchCatalog(
            MerchantSemanticSearchResult merchant,
            String query,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters,
            int limit
    ) {
        try {
            CatalogSearchCapability capability = capability(
                    CatalogSearchCapability.TOOL_NAME,
                    CatalogSearchCapability.class
            );
            MerchantMcpToolCallResult result = merchantMcpToolClient.callTool(
                    merchant,
                    CatalogSearchCapability.TOOL_NAME,
                    capability.buildArguments(
                            new CatalogSearchRequest(query, context, signals, filters, limit),
                            NegotiatedCapabilities.none()
                    )
            );
            CatalogSearchResponse response = capability.parseResponse(toolResponse(result));
            return new CatalogSearchResult(
                    result.endpoint(),
                    safeNonNullList(response.products()),
                    result.negotiatedCapabilities()
            );
        } catch (MerchantCatalogSearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MerchantCatalogSearchException(
                    "MCP catalog search failed for all endpoint candidates for " + merchant.domain(),
                    exception
            );
        }
    }

    public CatalogLookupResult lookupCatalog(
            MerchantSemanticSearchResult merchant,
            String productId,
            CatalogSearchContext context,
            NegotiatedCapabilities activeCapabilities
    ) {
        try {
            CatalogLookupCapability capability = capability(
                    CatalogLookupCapability.TOOL_NAME,
                    CatalogLookupCapability.class
            );
            MerchantMcpToolCallResult result = merchantMcpToolClient.callTool(
                    merchant,
                    CatalogLookupCapability.TOOL_NAME,
                    capability.buildArguments(
                            new CatalogLookupRequest(productId, context),
                            activeCapabilities
                    )
            );
            CatalogLookupResponse response = capability.parseResponse(toolResponse(result));
            return new CatalogLookupResult(
                    result.endpoint(),
                    response.resolvedProductId(productId),
                    response.resolvedProduct(),
                    result.negotiatedCapabilities()
            );
        } catch (RuntimeException exception) {
            throw new MerchantProductDetailsException("MCP catalog lookup failed for product " + productId, exception);
        }
    }

    public ProductDetailsResult getProduct(
            MerchantSemanticSearchResult merchant,
            String productId,
            CatalogSearchContext context,
            NegotiatedCapabilities activeCapabilities
    ) {
        try {
            CatalogGetProductCapability capability = capability(
                    CatalogGetProductCapability.TOOL_NAME,
                    CatalogGetProductCapability.class
            );
            MerchantMcpToolCallResult result = merchantMcpToolClient.callTool(
                    merchant,
                    CatalogGetProductCapability.TOOL_NAME,
                    capability.buildArguments(
                            new CatalogGetProductRequest(productId, context),
                            activeCapabilities
                    )
            );
            ProductDetailsResponse response = capability.parseResponse(toolResponse(result));
            return new ProductDetailsResult(
                    result.endpoint(),
                    result.contentText(),
                    response.product(),
                    result.negotiatedCapabilities()
            );
        } catch (MerchantProductDetailsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MerchantProductDetailsException("MCP get_product failed for product " + productId, exception);
        }
    }

    private UcpToolResponse toolResponse(MerchantMcpToolCallResult result) {
        return new UcpToolResponse(
                result.contentText(),
                result.structuredContent(),
                result.negotiatedCapabilities()
        );
    }

    private <T extends UcpCapability<?, ?>> T capability(String toolName, Class<T> type) {
        UcpCapability<?, ?> capability = capabilityRegistry.capabilityForTool(toolName);
        if (!type.isInstance(capability)) {
            throw new IllegalStateException(
                    "UCP tool " + toolName + " was registered to " + capability.getClass().getSimpleName()
            );
        }
        return type.cast(capability);
    }
}
