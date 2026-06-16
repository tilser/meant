package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.service.dto.CatalogSearchArguments;
import com.meant.api.module.merchant.service.dto.CatalogSearchCatalog;
import com.meant.api.module.merchant.service.dto.CatalogSearchPagination;
import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MerchantCatalogSearchClient {

    private static final String SEARCH_CATALOG_TOOL = "search_catalog";

    private final MerchantMcpToolClient merchantMcpToolClient;
    private final ObjectMapper objectMapper;

    public CatalogSearchResult searchCatalog(MerchantSemanticSearchResult merchant, String query, int limit) {
        try {
            var result = merchantMcpToolClient.callTool(merchant, SEARCH_CATALOG_TOOL, catalogRequest(query, limit));
            return new CatalogSearchResult(result.endpoint(), parseProducts(result.contentText()));
        } catch (MerchantCatalogSearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MerchantCatalogSearchException(
                    "MCP catalog search failed for all endpoint candidates for " + merchant.domain(),
                    exception
            );
        }
    }

    private List<CatalogSearchResponse.Product> parseProducts(String contentText) {
        try {
            CatalogSearchResponse response = objectMapper.readValue(contentText, CatalogSearchResponse.class);
            if (response == null || response.products() == null) {
                return List.of();
            }
            return response.products();
        } catch (JacksonException exception) {
            throw new MerchantCatalogSearchException("MCP catalog content was not a product search response", exception);
        }
    }

    private CatalogSearchArguments catalogRequest(String query, int limit) {
        return new CatalogSearchArguments(
                new CatalogSearchCatalog(
                        query,
                        new CatalogSearchPagination(null, limit)
                )
        );
    }
}
