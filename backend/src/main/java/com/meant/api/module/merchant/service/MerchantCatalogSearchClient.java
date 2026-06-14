package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.service.dto.CatalogSearchArguments;
import com.meant.api.module.merchant.service.dto.CatalogSearchCatalog;
import com.meant.api.module.merchant.service.dto.CatalogSearchPagination;
import com.meant.api.module.merchant.service.dto.CatalogSearchResponse;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.McpContent;
import com.meant.api.module.merchant.service.dto.McpToolCallParams;
import com.meant.api.module.merchant.service.dto.McpToolCallRequest;
import com.meant.api.module.merchant.service.dto.McpToolCallResponse;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class MerchantCatalogSearchClient {

    private static final String SEARCH_CATALOG_TOOL = "search_catalog";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MerchantCatalogSearchClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public CatalogSearchResult searchCatalog(MerchantSemanticSearchResult merchant, String query, int limit) {
        List<MerchantCatalogSearchException> failures = new ArrayList<>();
        for (String endpoint : endpointCandidates(merchant)) {
            try {
                return new CatalogSearchResult(endpoint, searchCatalogFromEndpoint(endpoint, query, limit));
            } catch (RestClientException | MerchantCatalogSearchException exception) {
                failures.add(new MerchantCatalogSearchException(
                        "MCP catalog search failed for " + endpoint,
                        exception
                ));
            }
        }
        throw catalogSearchException(merchant, failures);
    }

    private MerchantCatalogSearchException catalogSearchException(
            MerchantSemanticSearchResult merchant,
            List<MerchantCatalogSearchException> failures
    ) {
        if (failures.isEmpty()) {
            return new MerchantCatalogSearchException("No MCP endpoint candidates for " + merchant.domain());
        }
        MerchantCatalogSearchException exception = new MerchantCatalogSearchException(
                "MCP catalog search failed for all endpoint candidates for " + merchant.domain(),
                failures.getLast()
        );
        failures.stream()
                .limit(failures.size() - 1L)
                .forEach(exception::addSuppressed);
        return exception;
    }

    private List<CatalogSearchResponse.Product> searchCatalogFromEndpoint(String endpoint, String query, int limit) {
        McpToolCallResponse response = restClient.post()
                .uri(URI.create(endpoint))
                .body(catalogRequest(query, limit))
                .retrieve()
                .body(McpToolCallResponse.class);

        if (response == null) {
            throw new MerchantCatalogSearchException("MCP catalog response was empty");
        }
        if (response.error() != null) {
            throw new MerchantCatalogSearchException("MCP catalog error: " + response.error().message());
        }
        if (response.result() == null) {
            throw new MerchantCatalogSearchException("MCP catalog result was missing");
        }
        if (response.result().isError()) {
            throw new MerchantCatalogSearchException(
                    "MCP catalog result was marked as error: " + contentText(response.result().content())
            );
        }

        return parseProducts(contentText(response.result().content()));
    }

    private String contentText(List<McpContent> content) {
        return safeList(content).stream()
                .filter(item -> "text".equals(item.type()))
                .map(McpContent::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElseThrow(() -> new MerchantCatalogSearchException("MCP catalog result did not contain text content"));
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

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private McpToolCallRequest catalogRequest(String query, int limit) {
        return new McpToolCallRequest(
                "2.0",
                4,
                "tools/call",
                new McpToolCallParams(
                        SEARCH_CATALOG_TOOL,
                        new CatalogSearchArguments(
                                new CatalogSearchCatalog(
                                        query,
                                        new CatalogSearchPagination(null, limit)
                                )
                        )
                )
        );
    }

    private List<String> endpointCandidates(MerchantSemanticSearchResult merchant) {
        List<String> endpoints = new ArrayList<>();
        addEndpoint(endpoints, merchant.domain(), merchant.profileMcpEndpoint());
        endpoints.add("https://" + merchant.domain() + "/api/mcp");
        if (!merchant.domain().startsWith("www.")) {
            endpoints.add("https://www." + merchant.domain() + "/api/mcp");
        }
        return endpoints.stream().distinct().toList();
    }

    private void addEndpoint(List<String> endpoints, String domain, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        String trimmedEndpoint = endpoint.trim();
        if (trimmedEndpoint.startsWith("http://") || trimmedEndpoint.startsWith("https://")) {
            endpoints.add(trimmedEndpoint);
            return;
        }
        if (trimmedEndpoint.startsWith("/")) {
            endpoints.add("https://" + domain + trimmedEndpoint);
            return;
        }
        endpoints.add("https://" + trimmedEndpoint);
    }
}
