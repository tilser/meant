package com.meant.api.provider.shopify.cart;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.CartBindingMetrics;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.service.dto.CartToolCallContext;
import com.meant.api.module.cart.service.port.CartToolTransport;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.plugin.cart.cancel.CancelCartCapability;
import com.meant.api.plugin.cart.get.GetCartCapability;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shopify Cart MCP is merchant-scoped and may be called anonymously. */
@Component
@RequiredArgsConstructor
public class ShopifyCartToolTransport implements CartToolTransport {
    private final MerchantMcpToolClient merchantMcpToolClient;
    private final CartBindingMetrics metrics;
    private final ShopifyExternalOfferCartRoutingProvider routingProvider;
    private final MerchantOutboundUrlValidator urlValidator;

    @Override
    public boolean supports(CartRoutingTarget target) {
        return target.provider() == MerchantIntegrationProvider.SHOPIFY;
    }

    @Override
    public MerchantMcpToolCallResult call(
            CartRoutingTarget target,
            String toolName,
            Object arguments,
            CartToolCallContext context
    ) {
        try {
            return invoke(target, toolName, arguments, context);
        } catch (RuntimeException exception) {
            if (!isSafeToRetry(toolName, context)) {
                throw providerFailure(exception);
            }
            try {
                return invoke(routingProvider.refresh(target), toolName, arguments, context);
            } catch (RuntimeException retryFailure) {
                retryFailure.addSuppressed(exception);
                throw providerFailure(retryFailure);
            }
        }
    }

    private MerchantMcpToolCallResult invoke(
            CartRoutingTarget target, String toolName, Object arguments, CartToolCallContext context) {
        Map<String, String> headers = context.idempotencyKey() == null
                ? Map.of() : Map.of("Idempotency-Key", context.idempotencyKey().toString());
        return merchantMcpToolClient.callToolExactEndpoint(
                validatedProvider(target), toolName, arguments, headers);
    }

    private MerchantCartProvider validatedProvider(CartRoutingTarget target) {
        MerchantCartProvider provider = target.merchantProvider().forOperation(CommerceOperation.CART);
        String domain = provider.domain();
        if (domain == null || domain.isBlank()) {
            throw CartException.binding(CartException.BindingFailure.MISSING_ROUTING,
                    "Shopify cart route has no verified merchant domain");
        }
        String advertised = validated(domain, provider.advertisedMcpEndpoint());
        String profile = provider.profileMcpEndpoint() == null
                ? null : validated(domain, provider.profileMcpEndpoint());
        return new MerchantCartProvider(
                provider.merchantId(), domain, advertised, profile, provider.integrations(),
                provider.executionPolicy(), provider.profileCapturedAt(), provider.advertisedCapabilities());
    }

    private String validated(String domain, String endpoint) {
        String absolute = endpoint != null && endpoint.startsWith("/")
                ? "https://" + domain + endpoint : endpoint;
        return urlValidator.validateMerchantUrl(domain, absolute).toString();
    }

    private boolean isSafeToRetry(String toolName, CartToolCallContext context) {
        return GetCartCapability.TOOL_NAME.equals(toolName)
                || CancelCartCapability.TOOL_NAME.equals(toolName) && context.idempotencyKey() != null;
    }

    private CartException providerFailure(RuntimeException cause) {
        metrics.record(CartException.BindingFailure.PROVIDER_FAILURE);
        return CartException.bindingUpstream("Shopify cart provider call failed", cause);
    }
}
