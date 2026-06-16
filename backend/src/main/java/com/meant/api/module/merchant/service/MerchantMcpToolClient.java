package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.exception.MerchantMcpToolException;
import com.meant.api.module.merchant.properties.MerchantMcpToolProperties;
import com.meant.api.module.merchant.service.dto.McpContent;
import com.meant.api.module.merchant.service.dto.McpToolCallParams;
import com.meant.api.module.merchant.service.dto.McpToolCallRequest;
import com.meant.api.module.merchant.service.dto.McpToolCallResponse;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class MerchantMcpToolClient {

    private final RestClient restClient;

    @Autowired
    public MerchantMcpToolClient(
            RestClient.Builder restClientBuilder,
            MerchantMcpToolProperties merchantMcpToolProperties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(merchantMcpToolProperties.connectTimeoutMilliseconds()));
        requestFactory.setReadTimeout(Duration.ofMillis(merchantMcpToolProperties.readTimeoutMilliseconds()));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    MerchantMcpToolClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public MerchantMcpToolCallResult callTool(
            MerchantSemanticSearchResult merchant,
            String toolName,
            Object arguments
    ) {
        return callTool(
                merchant.domain(),
                merchant.advertisedMcpEndpoint(),
                merchant.profileMcpEndpoint(),
                toolName,
                arguments
        );
    }

    public MerchantMcpToolCallResult callTool(Merchant merchant, String toolName, Object arguments) {
        return callTool(
                merchant.getDomain(),
                merchant.getAdvertisedMcpEndpoint(),
                merchant.getProfileMcpEndpoint(),
                toolName,
                arguments
        );
    }

    private MerchantMcpToolCallResult callTool(
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint,
            String toolName,
            Object arguments
    ) {
        List<MerchantMcpToolException> failures = new ArrayList<>();
        for (String endpoint : endpointCandidates(domain, advertisedMcpEndpoint, profileMcpEndpoint)) {
            try {
                return new MerchantMcpToolCallResult(endpoint, callToolFromEndpoint(endpoint, toolName, arguments));
            } catch (RestClientException | MerchantMcpToolException exception) {
                failures.add(new MerchantMcpToolException("MCP tool call failed for " + endpoint, exception));
            }
        }
        throw mcpToolException(domain, toolName, failures);
    }

    private String callToolFromEndpoint(String endpoint, String toolName, Object arguments) {
        McpToolCallResponse response = restClient.post()
                .uri(URI.create(endpoint))
                .body(toolRequest(toolName, arguments))
                .retrieve()
                .body(McpToolCallResponse.class);

        if (response == null) {
            throw new MerchantMcpToolException("MCP response was empty");
        }
        if (response.error() != null) {
            throw new MerchantMcpToolException("MCP error: " + response.error().message());
        }
        if (response.result() == null) {
            throw new MerchantMcpToolException("MCP result was missing");
        }
        if (response.result().isError()) {
            throw new MerchantMcpToolException(
                    "MCP result was marked as error: " + contentText(response.result().content())
            );
        }
        return contentText(response.result().content());
    }

    private MerchantMcpToolException mcpToolException(
            String domain,
            String toolName,
            List<MerchantMcpToolException> failures
    ) {
        if (failures.isEmpty()) {
            return new MerchantMcpToolException("No MCP endpoint candidates for " + domain);
        }
        MerchantMcpToolException exception = new MerchantMcpToolException(
                "MCP tool " + toolName + " failed for all endpoint candidates for " + domain,
                failures.getLast()
        );
        failures.stream()
                .limit(failures.size() - 1L)
                .forEach(exception::addSuppressed);
        return exception;
    }

    private String contentText(List<McpContent> content) {
        return safeList(content).stream()
                .filter(item -> "text".equals(item.type()))
                .map(McpContent::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElseThrow(() -> new MerchantMcpToolException("MCP result did not contain text content"));
    }

    private McpToolCallRequest toolRequest(String toolName, Object arguments) {
        return new McpToolCallRequest(
                "2.0",
                4,
                "tools/call",
                new McpToolCallParams(toolName, arguments)
        );
    }

    private List<String> endpointCandidates(
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint
    ) {
        List<String> endpoints = new ArrayList<>();
        addEndpoint(endpoints, domain, profileMcpEndpoint);
        addEndpoint(endpoints, domain, advertisedMcpEndpoint);
        endpoints.add("https://" + domain + "/api/mcp");
        if (!domain.startsWith("www.")) {
            endpoints.add("https://www." + domain + "/api/mcp");
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

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
