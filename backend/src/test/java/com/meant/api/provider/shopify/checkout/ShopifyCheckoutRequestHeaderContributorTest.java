package com.meant.api.provider.shopify.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShopifyCheckoutRequestHeaderContributorTest {

    private final ShopifyCheckoutRequestHeaderContributor contributor =
            new ShopifyCheckoutRequestHeaderContributor();

    @Test
    void contributesTrustedBuyerIpForShopifyDirectCompletion() {
        MerchantCartProvider provider = provider(MerchantIntegrationProvider.SHOPIFY);

        assertThat(contributor.supports(provider, CommerceOperation.DIRECT_CHECKOUT_COMPLETION)).isTrue();
        assertThat(contributor.headers(CheckoutToolCallContext.forBuyer("203.0.113.42")))
                .containsExactly(Map.entry(
                        ShopifyCheckoutRequestHeaderContributor.BUYER_IP_HEADER,
                        "203.0.113.42"
                ));
    }

    @Test
    void doesNotApplyShopifyWireHeaderToGenericUcpProvider() {
        assertThat(contributor.supports(
                provider(MerchantIntegrationProvider.GENERIC_UCP),
                CommerceOperation.DIRECT_CHECKOUT_COMPLETION
        )).isFalse();
    }

    private MerchantCartProvider provider(MerchantIntegrationProvider integrationProvider) {
        MerchantExecutionPolicy unavailable = MerchantExecutionPolicy.unavailable();
        MerchantExecutionPolicy policy = new MerchantExecutionPolicy(Arrays.stream(CommerceOperation.values())
                .map(operation -> operation == CommerceOperation.DIRECT_CHECKOUT_COMPLETION
                        ? new CommerceCapabilityDecision(
                                operation,
                                true,
                                CapabilityAuthorizationDecision.ready("TOKEN", "TOKEN", Set.of()),
                                true,
                                CapabilityIntegrationHealth.HEALTHY,
                                false,
                                CapabilityAvailability.AVAILABLE,
                                CommerceExecutionRail.DIRECT_CHECKOUT_COMPLETION,
                                List.of(),
                                null,
                                integrationProvider
                        )
                        : unavailable.decision(operation))
                .toList());
        return new MerchantCartProvider(null, "shop.test", "https://shop.test/api/ucp/mcp", null,
                List.of(), policy);
    }
}
