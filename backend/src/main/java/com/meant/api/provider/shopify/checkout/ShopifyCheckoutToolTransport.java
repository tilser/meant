package com.meant.api.provider.shopify.checkout;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.checkout.service.port.CheckoutToolTransport;
import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.plugin.checkout.get.GetCheckoutCapability;
import com.meant.api.provider.shopify.auth.ShopifyMerchantUcpTransport;
import com.meant.api.provider.shopify.auth.ShopifyUcpTransportException;
import com.meant.api.provider.shopify.auth.ShopifyCommerceFailureMapper;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import com.meant.api.provider.shopify.capability.ShopifyAuthorizationTier;
import com.meant.api.provider.shopify.capability.ShopifyCapabilityReadinessProperties;
import com.meant.api.provider.shopify.cart.ShopifyCartProperties;
import com.meant.api.provider.shopify.cart.ShopifyExternalOfferCartRoutingProvider;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Authenticated Shopify Checkout MCP adapter bound to one verified merchant authority. */
@Component
@RequiredArgsConstructor
public class ShopifyCheckoutToolTransport implements CheckoutToolTransport {
    private final ShopifyMerchantUcpTransport merchantTransport;
    private final ShopifyExternalOfferCartRoutingProvider externalRoutingProvider;
    private final ShopifyCartProperties properties;
    private final ShopifyAgentAuthProperties authenticationProperties;
    private final ShopifyCapabilityReadinessProperties readinessProperties;

    @Override
    public boolean supports(CartRoutingTarget target) {
        return target.provider() == MerchantIntegrationProvider.SHOPIFY;
    }

    @Override
    public MerchantMcpToolCallResult call(
            CartRoutingTarget target, String toolName, Object arguments, CheckoutToolCallContext context) {
        CartRoutingTarget ready = readyTarget(target);
        Map<String, String> headers = context.idempotencyKey() == null ? Map.of()
                : Map.of("Idempotency-Key", context.idempotencyKey().toString());
        try {
            return merchantTransport.call(ready, CommerceOperation.CHECKOUT_SESSION, toolName, arguments,
                    headers, context.unauthorizedRefreshAllowed());
        } catch (ShopifyUcpTransportException exception) {
            // Reads may be retried by callers. Mutations must first reconcile their remote state.
            String message = GetCheckoutCapability.TOOL_NAME.equals(toolName)
                    ? "Shopify checkout read failed" : "Shopify checkout mutation outcome is unknown";
            throw CartException.bindingUpstream(message, ShopifyCommerceFailureMapper.map(exception));
        }
    }

    private CartRoutingTarget readyTarget(CartRoutingTarget target) {
        if (target.merchantIntegrationId() != null) {
            CommerceCapabilityDecision decision = target.merchantProvider().executionPolicy()
                    .decision(CommerceOperation.CHECKOUT_SESSION);
            if (!decision.available()
                    || !Objects.equals(decision.integrationId(), target.merchantIntegrationId())
                    || decision.provider() != target.provider()
                    || !decision.authorization().ready()
                    || !"TOKEN".equals(decision.authorization().requiredTier())) {
                throw CartException.binding(CartException.BindingFailure.MISSING_ROUTING,
                        "The cart's immutable integration is not ready for Shopify checkout");
            }
            return target;
        }
        CartRoutingTarget current = target;
        boolean stale = current.merchantProvider().profileCapturedAt() == null
                || !current.merchantProvider().profileCapturedAt().isAfter(
                        Instant.now().minus(properties.profileFreshness()));
        boolean checkoutAdvertised = current.merchantProvider().advertisedCapabilities().stream()
                .anyMatch(name -> name.startsWith("dev.ucp.shopping.checkout"));
        if (stale || !checkoutAdvertised) {
            try {
                current = externalRoutingProvider.refreshForCheckout(current);
            } catch (RuntimeException exception) {
                throw CartException.binding(CartException.BindingFailure.MISSING_ROUTING,
                        "The external Shopify merchant does not advertise a ready checkout capability");
            }
        }
        CommerceCapabilityDecision decision = current.merchantProvider().executionPolicy()
                .decision(CommerceOperation.CHECKOUT_SESSION);
        if (!decision.available() || !decision.authorization().ready()
                || !"TOKEN".equals(decision.authorization().requiredTier())
                || !authenticationProperties.isEnabled()
                || readinessProperties.authorizationTier().level() < ShopifyAuthorizationTier.TOKEN.level()) {
            throw CartException.binding(CartException.BindingFailure.MISSING_ROUTING,
                    "The external Shopify merchant is not ready for token-tier checkout");
        }
        return current;
    }
}
