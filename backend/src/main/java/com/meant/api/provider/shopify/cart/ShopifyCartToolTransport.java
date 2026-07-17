package com.meant.api.provider.shopify.cart;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.CartBindingMetrics;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.service.dto.CartToolCallContext;
import com.meant.api.module.cart.service.port.CartToolTransport;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.plugin.cart.cancel.CancelCartCapability;
import com.meant.api.plugin.cart.get.GetCartCapability;
import com.meant.api.provider.shopify.auth.ShopifyMerchantUcpTransport;
import com.meant.api.provider.shopify.auth.ShopifyCommerceFailureMapper;
import com.meant.api.provider.shopify.auth.ShopifyUcpTransportException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shopify Cart MCP is merchant-scoped and may be called anonymously. */
@Component
@RequiredArgsConstructor
public class ShopifyCartToolTransport implements CartToolTransport {
    static final String BUYER_IP_HEADER = "Shopify-Buyer-IP";

    private final ShopifyMerchantUcpTransport merchantTransport;
    private final CartBindingMetrics metrics;
    private final ShopifyExternalOfferCartRoutingProvider routingProvider;
    private final ShopifyCartRetryPolicy retryPolicy;

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
            if (!isSafeToRetry(toolName, context) || !retryPolicy.prepare(exception)) {
                throw providerFailure(exception);
            }
            CartRoutingTarget retryTarget = target;
            if (target.merchantIntegrationId() == null && retryPolicy.refreshExternalRoute(exception)) {
                retryTarget = routingProvider.refresh(target);
            }
            try {
                return invoke(retryTarget, toolName, arguments, context, false);
            } catch (RuntimeException retryFailure) {
                retryFailure.addSuppressed(exception);
                throw providerFailure(retryFailure);
            }
        }
    }

    private MerchantMcpToolCallResult invoke(
            CartRoutingTarget target, String toolName, Object arguments, CartToolCallContext context) {
        return invoke(target, toolName, arguments, context, true);
    }

    private MerchantMcpToolCallResult invoke(
            CartRoutingTarget target, String toolName, Object arguments, CartToolCallContext context,
            boolean unauthorizedRefreshAllowed) {
        Map<String, String> headers = headers(context);
        return merchantTransport.call(
                target, CommerceOperation.CART, toolName, arguments, headers, unauthorizedRefreshAllowed);
    }

    private Map<String, String> headers(CartToolCallContext context) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (context.idempotencyKey() != null) {
            headers.put("Idempotency-Key", context.idempotencyKey().toString());
        }
        if (context.buyerIp() != null) {
            headers.put(BUYER_IP_HEADER, context.buyerIp());
        }
        return Map.copyOf(headers);
    }

    private boolean isSafeToRetry(String toolName, CartToolCallContext context) {
        return GetCartCapability.TOOL_NAME.equals(toolName)
                || CancelCartCapability.TOOL_NAME.equals(toolName) && context.idempotencyKey() != null;
    }

    private CartException providerFailure(RuntimeException cause) {
        metrics.record(CartException.BindingFailure.PROVIDER_FAILURE);
        Throwable redacted = cause instanceof ShopifyUcpTransportException transport
                ? ShopifyCommerceFailureMapper.map(transport) : cause;
        return CartException.bindingUpstream("Shopify cart provider call failed", redacted);
    }
}
