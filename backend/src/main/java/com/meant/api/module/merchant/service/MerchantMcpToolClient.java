package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.exception.MerchantMcpToolException;
import com.meant.api.module.merchant.exception.MerchantOutboundUrlException;
import com.meant.api.module.merchant.properties.MerchantMcpToolProperties;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolsListFetchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import com.meant.api.plugin.transport.client.UcpMcpClient;
import com.meant.api.plugin.transport.client.UcpMcpException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class MerchantMcpToolClient {

    private final RestClient restClient;
    private final MerchantOutboundUrlValidator merchantOutboundUrlValidator;
    private final UcpMcpClient ucpMcpClient;
    private final Duration merchantTimeout;

    @Autowired
    public MerchantMcpToolClient(
            RestClient.Builder restClientBuilder,
            MerchantMcpToolProperties merchantMcpToolProperties,
            MerchantOutboundUrlValidator merchantOutboundUrlValidator,
            UcpMcpClient ucpMcpClient
    ) {
        MerchantClientHttpRequestFactory requestFactory = new MerchantClientHttpRequestFactory(
                merchantOutboundUrlValidator,
                Duration.ofMillis(merchantMcpToolProperties.connectTimeoutMilliseconds()),
                Duration.ofMillis(merchantMcpToolProperties.readTimeoutMilliseconds())
        );
        this.restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
        this.merchantOutboundUrlValidator = merchantOutboundUrlValidator;
        this.ucpMcpClient = ucpMcpClient;
        this.merchantTimeout = Duration.ofMillis(merchantMcpToolProperties.merchantTimeoutMilliseconds());
    }

    public MerchantMcpToolClient(RestClient restClient) {
        this(restClient, new MerchantOutboundUrlValidator());
    }

    public MerchantMcpToolClient(RestClient restClient, MerchantOutboundUrlValidator merchantOutboundUrlValidator) {
        this(
                restClient,
                merchantOutboundUrlValidator,
                new UcpMcpClient(new AgentIdentity(
                        URI.create("http://localhost:8080/.well-known/ucp-agent.json"),
                        "2026-04-08",
                        "meant-test"
                )),
                Duration.ofSeconds(5)
        );
    }

    public MerchantMcpToolClient(
            RestClient restClient,
            MerchantOutboundUrlValidator merchantOutboundUrlValidator,
            UcpMcpClient ucpMcpClient,
            Duration merchantTimeout
    ) {
        this.restClient = restClient;
        this.merchantOutboundUrlValidator = merchantOutboundUrlValidator;
        this.ucpMcpClient = ucpMcpClient;
        this.merchantTimeout = merchantTimeout;
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

    public MerchantMcpToolsListFetchResult listTools(Merchant merchant) {
        return listTools(
                merchant.getDomain(),
                merchant.getAdvertisedMcpEndpoint(),
                merchant.getProfileMcpEndpoint()
        );
    }

    public MerchantMcpToolsListFetchResult listTools(
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint
    ) {
        EndpointResult<String> result = executeWithEndpointFallback(
                domain,
                advertisedMcpEndpoint,
                profileMcpEndpoint,
                "MCP tools/list",
                endpoint -> ucpMcpClient.listTools(restClient, endpoint)
        );
        return new MerchantMcpToolsListFetchResult(result.endpoint(), result.value());
    }

    public MerchantMcpToolCallResult callTool(MerchantCartProvider provider, String toolName, Object arguments) {
        return callTool(provider, toolName, arguments, java.util.Map.of());
    }

    public MerchantMcpToolCallResult callTool(
            MerchantCartProvider provider,
            String toolName,
            Object arguments,
            java.util.Map<String, String> headers
    ) {
        return callTool(
                provider.domain(),
                provider.advertisedMcpEndpoint(),
                provider.profileMcpEndpoint(),
                toolName,
                arguments,
                headers
        );
    }

    private MerchantMcpToolCallResult callTool(
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint,
            String toolName,
            Object arguments
    ) {
        return callTool(domain, advertisedMcpEndpoint, profileMcpEndpoint, toolName, arguments, java.util.Map.of());
    }

    private MerchantMcpToolCallResult callTool(
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint,
            String toolName,
            Object arguments,
            java.util.Map<String, String> headers
    ) {
        EndpointResult<UcpToolResponse> result = executeWithEndpointFallback(
                domain,
                advertisedMcpEndpoint,
                profileMcpEndpoint,
                "MCP tool " + toolName,
                endpoint -> {
                    UcpToolResponse response = ucpMcpClient.callTool(restClient, endpoint, toolName, arguments, headers);
                    return response;
                }
        );
        UcpToolResponse response = result.value();
        return new MerchantMcpToolCallResult(
                result.endpoint(),
                response.textContent(),
                response.structuredContent(),
                response.negotiatedCapabilities()
        );
    }

    private <T> EndpointResult<T> executeWithEndpointFallback(
            String domain,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint,
            String operation,
            Function<URI, T> endpointCall
    ) {
        List<MerchantMcpToolException> failures = new ArrayList<>();
        Instant deadline = Instant.now().plus(merchantTimeout);
        for (String endpoint : endpointCandidates(domain, advertisedMcpEndpoint, profileMcpEndpoint)) {
            if (Instant.now().isAfter(deadline)) {
                failures.add(new MerchantMcpToolException(operation + " exceeded merchant deadline"));
                break;
            }
            try {
                URI endpointUri = merchantOutboundUrlValidator.validateOutboundUrl(endpoint);
                return new EndpointResult<>(endpointUri.toString(), endpointCall.apply(endpointUri));
            } catch (RestClientException
                     | MerchantMcpToolException
                     | MerchantOutboundUrlException
                     | UcpMcpException exception) {
                failures.add(new MerchantMcpToolException(operation + " failed for " + endpoint, exception));
                if (MerchantHttpFailureClassifier.isRateLimited(exception)) {
                    break;
                }
            }
        }
        throw mcpToolException(domain, operation, failures);
    }

    private MerchantMcpToolException mcpToolException(
            String domain,
            String operation,
            List<MerchantMcpToolException> failures
    ) {
        if (failures.isEmpty()) {
            return new MerchantMcpToolException("No MCP endpoint candidates for " + domain);
        }
        MerchantMcpToolException exception = new MerchantMcpToolException(
                operation + " failed for all endpoint candidates for " + domain,
                failures.getLast()
        );
        failures.stream()
                .limit(failures.size() - 1L)
                .forEach(exception::addSuppressed);
        return exception;
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

    private record EndpointResult<T>(
            String endpoint,
            T value
    ) {
    }
}
