package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutResultMapperLifecycleTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CheckoutResultMapper mapper = new CheckoutResultMapper(
            objectMapper, new CheckoutExecutionPlanner());

    @Test
    void cachedProviderLifecycleDrivesEveryPersistedPlannerBranch() {
        assertAction("RECOVERABLE_FAILURE", CheckoutNextAction.UPDATE_CHECKOUT);
        assertAction("TERMINAL_FAILURE", CheckoutNextAction.RESTART);
        assertAction("READY_FOR_COMPLETE", CheckoutNextAction.HANDOFF);
        assertAction("PROCESSING", CheckoutNextAction.WAIT);
        assertAction("COMPLETED", CheckoutNextAction.DONE);
        assertAction("CANCELLED", CheckoutNextAction.RESTART);
        assertAction("MERCHANT_HANDOFF_REQUIRED", CheckoutNextAction.HANDOFF);
        assertAction("UNKNOWN", CheckoutNextAction.UNKNOWN);
    }

    @Test
    void liveResponseFactsAreReturnedBeforeProviderPayloadIsDiscarded() throws Exception {
        Cart cart = cart("INCOMPLETE");
        UcpCheckoutResponse response = objectMapper.readValue("""
                {"ucp":{"version":"2026-04-08"},"checkout":{"id":"checkout-1","status":"incomplete",
                 "currency":"USD","total_amount":{"minorAmount":1234},
                 "messages":[{"code":"address_required","severity":"recoverable","content":"Address required"}]}}
                """, UcpCheckoutResponse.class);

        var live = mapper.from(cart, response, MerchantExecutionPolicy.unavailable());
        var cached = mapper.from(cart, MerchantExecutionPolicy.unavailable());

        assertThat(live.totalAmountMinor()).isEqualTo(1234);
        assertThat(live.messages()).extracting("code").containsExactly("address_required");
        assertThat(cached.totalAmountMinor()).isNull();
        assertThat(cached.messages()).isEmpty();
        assertThat(cached.nextAction()).isEqualTo(CheckoutNextAction.UPDATE_CHECKOUT);
    }

    @Test
    void cachedProviderRedirectStillUsesAvailableEmbeddedCheckout() {
        MerchantExecutionPolicy policy = new MerchantExecutionPolicy(List.of(
                new CommerceCapabilityDecision(
                        CommerceOperation.EMBEDDED_CHECKOUT,
                        true,
                        CapabilityAuthorizationDecision.notRequired(),
                        true,
                        CapabilityIntegrationHealth.HEALTHY,
                        true,
                        CapabilityAvailability.AVAILABLE,
                        CommerceExecutionRail.EMBEDDED_CHECKOUT,
                        List.of(),
                        UUID.randomUUID(),
                        null
                )
        ));

        var result = mapper.from(cart("MERCHANT_HANDOFF_REQUIRED"), policy);

        assertThat(result.nextAction()).isEqualTo(CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT);
        assertThat(result.selectedRail()).isEqualTo(CommerceExecutionRail.EMBEDDED_CHECKOUT);
        assertThat(result.ineligibilityReasons()).isEmpty();
    }

    private void assertAction(String lifecycle, CheckoutNextAction action) {
        assertThat(mapper.from(cart(lifecycle), MerchantExecutionPolicy.unavailable()).nextAction()).isEqualTo(action);
    }

    private Cart cart(String lifecycle) {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).endpoint("https://shop.test/api/ucp/mcp")
                .remoteCartId("cart-1").remoteCartIdHash("hash").routingScopeKey("SHOPIFY:integration:one")
                .provider("SHOPIFY").checkoutId("checkout-1").checkoutStatus("wire-status")
                .checkoutLifecycleState(lifecycle).checkoutSynchronizedAt(Instant.now())
                .rawCartResponse("{}").totalQuantity(1).active(true)
                .createdAt(Instant.now()).updatedAt(Instant.now()).refreshedAt(Instant.now()).build();
        return cart;
    }
}
