package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.exception.MerchantOutboundUrlException;
import com.meant.api.module.merchant.service.dto.MerchantMcpProfileResult;
import com.meant.api.module.merchant.service.dto.StorePolicyFaqEntry;
import com.meant.api.module.merchant.service.dto.StoreProfileArguments;
import com.meant.api.plugin.transport.dto.McpContent;
import com.meant.api.plugin.transport.dto.McpToolCallParams;
import com.meant.api.plugin.transport.dto.McpToolCallRequest;
import com.meant.api.plugin.transport.dto.McpToolCallResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class MerchantDomainMcpClient {

    private static final String PROFILE_QUERY = "Tell me about your store?";
    private static final TypeReference<List<StorePolicyFaqEntry>> STORE_POLICY_TYPE = new TypeReference<>() {
    };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MerchantOutboundUrlValidator merchantOutboundUrlValidator;

    @Autowired
    public MerchantDomainMcpClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            MerchantOutboundUrlValidator merchantOutboundUrlValidator
    ) {
        this.restClient = restClientBuilder.clone()
                .requestFactory(new MerchantClientHttpRequestFactory(merchantOutboundUrlValidator))
                .build();
        this.objectMapper = objectMapper;
        this.merchantOutboundUrlValidator = merchantOutboundUrlValidator;
    }

    public MerchantDomainMcpClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this(restClientBuilder, objectMapper, new MerchantOutboundUrlValidator());
    }

    public MerchantMcpProfileResult fetchStoreProfile(String domain) {
        List<String> endpoints = endpointCandidates(domain);
        MerchantEnrichmentException lastException = null;
        for (String endpoint : endpoints) {
            try {
                URI endpointUri = merchantOutboundUrlValidator.validateMerchantUrl(domain, endpoint);
                return new MerchantMcpProfileResult(
                        endpointUri.toString(),
                        fetchStoreProfileFromEndpoint(endpointUri)
                );
            } catch (RestClientException | MerchantEnrichmentException | MerchantOutboundUrlException exception) {
                lastException = new MerchantEnrichmentException("MCP profile fetch failed for " + endpoint, exception);
            }
        }
        throw lastException == null
                ? new MerchantEnrichmentException("No MCP endpoint candidates for " + domain)
                : lastException;
    }

    public StorePolicyFaqEntry parseStorePolicyFaqContent(String contentText) {
        try {
            List<StorePolicyFaqEntry> entries = objectMapper.readValue(contentText, STORE_POLICY_TYPE);
            return entries.stream()
                    .filter(entry -> entry.question() != null && entry.answer() != null)
                    .findFirst()
                    .orElseThrow(() -> new MerchantEnrichmentException("MCP profile content did not contain question/answer"));
        } catch (JacksonException exception) {
            throw new MerchantEnrichmentException("MCP profile content was not a typed store FAQ array", exception);
        }
    }

    private StorePolicyFaqEntry fetchStoreProfileFromEndpoint(URI endpoint) {
        McpToolCallResponse response = restClient.post()
                .uri(endpoint)
                .body(request())
                .retrieve()
                .body(McpToolCallResponse.class);

        if (response == null) {
            throw new MerchantEnrichmentException("MCP response was empty");
        }
        if (response.error() != null) {
            throw new MerchantEnrichmentException("MCP error: " + response.error().message());
        }
        if (response.result() == null || response.result().isError()) {
            throw new MerchantEnrichmentException("MCP result was missing or marked as error");
        }

        String contentText = response.result().content().stream()
                .filter(content -> "text".equals(content.type()))
                .map(McpContent::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElseThrow(() -> new MerchantEnrichmentException("MCP result did not contain text content"));
        return parseStorePolicyFaqContent(contentText);
    }

    private McpToolCallRequest request() {
        return new McpToolCallRequest(
                "2.0",
                3,
                "tools/call",
                new McpToolCallParams(
                        "search_shop_policies_and_faqs",
                        new StoreProfileArguments(PROFILE_QUERY)
                )
        );
    }

    private List<String> endpointCandidates(String domain) {
        if (domain == null || domain.isBlank()) {
            return List.of();
        }
        String trimmedDomain = domain.trim();
        List<String> endpoints = new ArrayList<>();
        endpoints.add("https://" + trimmedDomain + "/api/mcp");
        if (!trimmedDomain.startsWith("www.")) {
            endpoints.add("https://www." + trimmedDomain + "/api/mcp");
        }
        return endpoints.stream().distinct().toList();
    }
}
