package com.meant.api.plugin.transport.profile;

import com.meant.api.module.merchant.constant.CapabilityAuthorizationStatus;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.service.MerchantCapabilityReadinessAdapter;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.MerchantCapabilityReadinessContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GenericUcpCapabilityReadinessAdapter implements MerchantCapabilityReadinessAdapter {

    private static final String CHECKOUT_CAPABILITY = "dev.ucp.shopping.checkout";
    private static final String COMPLETE_CHECKOUT_CAPABILITY = "dev.ucp.shopping.checkout.complete";

    private final GenericUcpCapabilityReadinessProperties properties;

    @Override
    public MerchantIntegrationProvider provider() {
        return MerchantIntegrationProvider.GENERIC_UCP;
    }

    @Override
    public boolean advertised(CommerceOperation operation, MerchantCapabilityReadinessContext context) {
        return switch (operation) {
            case CATALOG -> context.hasRole(MerchantIntegrationRole.CATALOG_PROVENANCE)
                    || context.hasRole(MerchantIntegrationRole.STOREFRONT_CATALOG);
            case CART -> context.hasRole(MerchantIntegrationRole.CART);
            case CHECKOUT_SESSION -> context.hasRole(MerchantIntegrationRole.CHECKOUT);
            case EMBEDDED_CHECKOUT -> false;
            case DIRECT_CHECKOUT_COMPLETION -> context.hasRole(MerchantIntegrationRole.CHECKOUT)
                    && (context.advertises(CHECKOUT_CAPABILITY)
                    || context.advertises(COMPLETE_CHECKOUT_CAPABILITY));
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
            case EMBEDDED_CHECKOUT -> CapabilityAuthorizationDecision.unavailable(
                    CapabilityAuthorizationStatus.UNSUPPORTED
            );
            case DIRECT_CHECKOUT_COMPLETION -> configured(properties.isDirectCheckoutCompletionAuthorized());
            case ORDER_READS -> configured(properties.isOrderReadsAuthorized());
            case ORDER_WEBHOOKS -> configured(properties.isOrderWebhooksAuthorized());
        };
    }

    private CapabilityAuthorizationDecision configured(boolean authorized) {
        return authorized
                ? CapabilityAuthorizationDecision.ready("EXPLICIT_GRANT", "EXPLICIT_GRANT", Set.of())
                : CapabilityAuthorizationDecision.unavailable(CapabilityAuthorizationStatus.NOT_AUTHORIZED);
    }
}
