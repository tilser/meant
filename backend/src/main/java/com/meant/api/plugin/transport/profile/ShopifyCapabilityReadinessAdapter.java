package com.meant.api.plugin.transport.profile;

import com.meant.api.module.merchant.constant.CapabilityAuthorizationStatus;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.service.MerchantCapabilityReadinessAdapter;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.MerchantCapabilityReadinessContext;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapts stable Shopify access configuration into the provider-neutral policy. This read path
 * intentionally does not call {@code ShopifyBearerAuthenticationStrategy.prepare}, because token
 * acquisition belongs to an authenticated execution attempt rather than checkout rendering.
 */
@Component
@RequiredArgsConstructor
public class ShopifyCapabilityReadinessAdapter implements MerchantCapabilityReadinessAdapter {

    private final ShopifyCapabilityReadinessProperties properties;
    private final ShopifyAgentAuthProperties authenticationProperties;

    @Override
    public MerchantIntegrationProvider provider() {
        return MerchantIntegrationProvider.SHOPIFY;
    }

    @Override
    public boolean advertised(CommerceOperation operation, MerchantCapabilityReadinessContext context) {
        return switch (operation) {
            case CATALOG -> context.hasRole(MerchantIntegrationRole.CATALOG_PROVENANCE)
                    || context.hasRole(MerchantIntegrationRole.STOREFRONT_CATALOG);
            case CART -> context.hasRole(MerchantIntegrationRole.CART);
            case CHECKOUT_SESSION -> context.hasRole(MerchantIntegrationRole.CHECKOUT);
            case EMBEDDED_CHECKOUT -> context.hasRole(MerchantIntegrationRole.CHECKOUT)
                    && properties.isEmbeddedCheckoutAdvertised();
            case DIRECT_CHECKOUT_COMPLETION -> context.hasRole(MerchantIntegrationRole.CHECKOUT)
                    && properties.isDirectCheckoutCompletionAdvertised();
            case ORDER_READS, ORDER_WEBHOOKS -> context.hasRole(MerchantIntegrationRole.ORDERS);
        };
    }

    @Override
    public CapabilityAuthorizationDecision authorization(
            CommerceOperation operation,
            MerchantCapabilityReadinessContext context
    ) {
        return switch (operation) {
            case CATALOG, CART, CHECKOUT_SESSION -> CapabilityAuthorizationDecision.notRequired();
            case EMBEDDED_CHECKOUT -> authorizedTier(
                    context,
                    properties.isEmbeddedCheckoutAuthorized(),
                    ShopifyAuthorizationTier.STANDARD,
                    Set.of()
            );
            case DIRECT_CHECKOUT_COMPLETION -> authorizedTier(
                    context,
                    properties.isDirectCheckoutCompletionAuthorized(),
                    ShopifyAuthorizationTier.TOKEN,
                    properties.directCheckoutCompletionRequiredScopes()
            );
            case ORDER_READS -> authorizedTier(
                    context,
                    properties.isOrderReadsAuthorized(),
                    ShopifyAuthorizationTier.TOKEN,
                    properties.orderReadsRequiredScopes()
            );
            case ORDER_WEBHOOKS -> properties.isOrderWebhooksAuthorized()
                    ? CapabilityAuthorizationDecision.notRequired()
                    : CapabilityAuthorizationDecision.unavailable(CapabilityAuthorizationStatus.NOT_AUTHORIZED);
        };
    }

    private CapabilityAuthorizationDecision authorizedTier(
            MerchantCapabilityReadinessContext context,
            boolean authorized,
            ShopifyAuthorizationTier requiredTier,
            Set<String> requiredScopes
    ) {
        if (!authorized) {
            return CapabilityAuthorizationDecision.unavailable(CapabilityAuthorizationStatus.NOT_AUTHORIZED);
        }
        ShopifyAuthorizationTier grantedTier = properties.authorizationTier();
        if (grantedTier.ordinal() < requiredTier.ordinal()) {
            return CapabilityAuthorizationDecision.tierNotGranted(requiredTier.name(), grantedTier.name());
        }
        if (requiredTier == ShopifyAuthorizationTier.TOKEN
                && (context.authStrategy() != MerchantIntegrationAuthStrategy.OAUTH_BEARER
                || !authenticationProperties.isEnabled())) {
            return CapabilityAuthorizationDecision.unavailable(CapabilityAuthorizationStatus.AUTHENTICATION_DISABLED);
        }
        Set<String> missingScopes = new TreeSet<>(requiredScopes);
        missingScopes.removeAll(properties.grantedScopes());
        if (!missingScopes.isEmpty()) {
            return CapabilityAuthorizationDecision.missingScopes(
                    requiredTier.name(),
                    grantedTier.name(),
                    requiredScopes,
                    missingScopes
            );
        }
        return CapabilityAuthorizationDecision.ready(requiredTier.name(), grantedTier.name(), requiredScopes);
    }
}
