package com.meant.api.provider.shopify.auth;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.provider.shopify.cart.ShopifyCartProperties;
import java.net.URI;
import java.util.Set;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Authenticated, exact-authority transport for merchant-scoped Shopify UCP tools. */
@Component
@RequiredArgsConstructor
public class ShopifyMerchantUcpTransport {
    private final ShopifyUcpClient client;
    private final ShopifyCartProperties properties;
    private final MerchantOutboundUrlValidator urlValidator;
    private final ShopifyCommerceMetrics metrics;

    public MerchantMcpToolCallResult call(
            CartRoutingTarget target,
            CommerceOperation operation,
            String toolName,
            Object arguments
    ) {
        return call(target, operation, toolName, arguments, Map.of());
    }

    public MerchantMcpToolCallResult call(
            CartRoutingTarget target, CommerceOperation operation, String toolName,
            Object arguments, Map<String, String> headers) {
        return call(target, operation, toolName, arguments, headers, true);
    }

    public MerchantMcpToolCallResult call(
            CartRoutingTarget target, CommerceOperation operation, String toolName,
            Object arguments, Map<String, String> headers, boolean unauthorizedRefreshAllowed) {
        MerchantCartProvider provider = validatedProvider(target, operation);
        URI endpoint = URI.create(provider.advertisedMcpEndpoint());
        long started = System.nanoTime();
        try {
            UcpToolResponse response = client.callTool(new ShopifyUcpRequestOptions(
                    endpoint,
                    Set.of(endpoint.getHost()),
                    Set.of(),
                    properties.connectTimeout(),
                    properties.readTimeout(),
                    properties.requestDeadline(),
                    unauthorizedRefreshAllowed
            ), toolName, arguments, headers);
            metrics.record(target, toolName, "success", System.nanoTime() - started);
            JsonNode structuredContent = response.structuredContent();
            String status = checkoutStatus(structuredContent);
            if (status != null) {
                metrics.recordStateObservation(target, toolName, status);
            }
            return new MerchantMcpToolCallResult(endpoint.toString(), response.textContent(),
                    structuredContent, response.negotiatedCapabilities());
        } catch (ShopifyUcpTransportException exception) {
            metrics.record(target, toolName, ShopifyCommerceFailureMapper.map(exception).kind().name(),
                    System.nanoTime() - started);
            throw exception;
        }
    }

    private String checkoutStatus(JsonNode structuredContent) {
        if (structuredContent == null || !structuredContent.isObject()) {
            return null;
        }
        JsonNode checkout = structuredContent.get("checkout");
        if (checkout != null && checkout.isObject() && checkout.path("status").isString()) {
            return checkout.path("status").stringValue();
        }
        JsonNode status = structuredContent.get("status");
        return status != null && status.isString() ? status.stringValue() : null;
    }

    private MerchantCartProvider validatedProvider(CartRoutingTarget target, CommerceOperation operation) {
        MerchantCartProvider provider = target.merchantIntegrationId() == null
                ? target.merchantProvider()
                : target.merchantProvider().forIntegration(target.merchantIntegrationId());
        String domain = provider.domain();
        if (domain == null || domain.isBlank()) {
            throw CartException.binding(CartException.BindingFailure.MISSING_ROUTING,
                    "Shopify merchant route has no verified domain");
        }
        String endpoint = validated(domain, provider.advertisedMcpEndpoint());
        String profile = provider.profileMcpEndpoint() == null
                ? null : validated(domain, provider.profileMcpEndpoint());
        return new MerchantCartProvider(
                provider.merchantId(), domain, endpoint, profile, provider.integrations(),
                provider.executionPolicy(), provider.profileCapturedAt(), provider.advertisedCapabilities());
    }

    private String validated(String domain, String endpoint) {
        String absolute = endpoint != null && endpoint.startsWith("/")
                ? "https://" + domain + endpoint : endpoint;
        return urlValidator.validateMerchantUrl(domain, absolute).toString();
    }
}
