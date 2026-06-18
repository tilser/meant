package com.meant.api.module.merchant.service;

import static com.meant.api.common.util.CollectionUtils.safeList;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.exception.MerchantMcpToolException;
import com.meant.api.module.merchant.exception.MerchantOutboundUrlException;
import com.meant.api.module.merchant.properties.MerchantMcpToolProperties;
import com.meant.api.module.merchant.service.dto.McpContent;
import com.meant.api.module.merchant.service.dto.McpToolCallParams;
import com.meant.api.module.merchant.service.dto.McpToolCallRequest;
import com.meant.api.module.merchant.service.dto.McpToolCallResponse;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class MerchantMcpToolClient {

    private final RestClient restClient;
    private final MerchantOutboundUrlValidator merchantOutboundUrlValidator;

    @Autowired
    public MerchantMcpToolClient(
            RestClient.Builder restClientBuilder,
            MerchantMcpToolProperties merchantMcpToolProperties,
            MerchantOutboundUrlValidator merchantOutboundUrlValidator
    ) {
        MerchantClientHttpRequestFactory requestFactory = new MerchantClientHttpRequestFactory(
                merchantOutboundUrlValidator,
                Duration.ofMillis(merchantMcpToolProperties.connectTimeoutMilliseconds()),
                Duration.ofMillis(merchantMcpToolProperties.readTimeoutMilliseconds())
        );
        this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
        this.merchantOutboundUrlValidator = merchantOutboundUrlValidator;
    }

    public MerchantMcpToolClient(RestClient restClient) {
        this(restClient, new MerchantOutboundUrlValidator());
    }

    public MerchantMcpToolClient(RestClient restClient, MerchantOutboundUrlValidator merchantOutboundUrlValidator) {
        this.restClient = restClient;
        this.merchantOutboundUrlValidator = merchantOutboundUrlValidator;
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

    public MerchantMcpToolCallResult callTool(MerchantCartProvider provider, String toolName, Object arguments) {
        return callTool(
                provider.domain(),
                provider.advertisedMcpEndpoint(),
                provider.profileMcpEndpoint(),
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
                URI endpointUri = merchantOutboundUrlValidator.validateMerchantUrl(domain, endpoint);
                return new MerchantMcpToolCallResult(
                        endpointUri.toString(),
                        callToolFromEndpoint(endpointUri, toolName, arguments)
                );
            } catch (RestClientException | MerchantMcpToolException | MerchantOutboundUrlException exception) {
                failures.add(new MerchantMcpToolException("MCP tool call failed for " + endpoint, exception));
            }
        }
        throw mcpToolException(domain, toolName, failures);
    }

    private String callToolFromEndpoint(URI endpoint, String toolName, Object arguments) {
        McpToolCallResponse response = restClient.post()
                .uri(endpoint)
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
        if (hasText(domain)) {
            String trimmedDomain = domain.trim();
            endpoints.add("https://" + trimmedDomain + "/api/mcp");
            if (!trimmedDomain.startsWith("www.")) {
                endpoints.add("https://www." + trimmedDomain + "/api/mcp");
            }
        }
        return endpoints.stream().distinct().toList();
    }

    private void addEndpoint(List<String> endpoints, String domain, String endpoint) {
        if (!hasText(endpoint)) {
            return;
        }
        String trimmedEndpoint = endpoint.trim();
        if (trimmedEndpoint.startsWith("http://") || trimmedEndpoint.startsWith("https://")) {
            endpoints.add(trimmedEndpoint);
            return;
        }
        if (trimmedEndpoint.startsWith("/")) {
            if (hasText(domain)) {
                endpoints.add("https://" + domain.trim() + trimmedEndpoint);
            }
            return;
        }
        endpoints.add("https://" + trimmedEndpoint);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
