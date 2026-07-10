package com.meant.api.module.cart.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutResponseTest {

    @Test
    void mapsExplicitExecutionContractAndDeprecatedCompatibilityView() {
        MerchantExecutionPolicy policy = MerchantExecutionPolicy.unavailable();
        CheckoutResult result = new CheckoutResult(
                UUID.randomUUID(),
                "remote-cart",
                "checkout-1",
                "ready_for_complete",
                null,
                "https://merchant.example/checkout",
                "2026-04-08",
                1200L,
                "USD",
                List.of(),
                CheckoutNextAction.HANDOFF,
                CommerceExecutionRail.MERCHANT_HANDOFF,
                List.of(
                        CapabilityIneligibilityReason.AUTHORIZATION_REQUIRED,
                        CapabilityIneligibilityReason.FALLBACK_SELECTED
                ),
                policy
        );

        CheckoutResponse response = CheckoutResponse.from(result);

        assertThat(response.nextAction()).isEqualTo(CheckoutNextAction.HANDOFF);
        assertThat(response.selectedRail()).isEqualTo(CommerceExecutionRail.MERCHANT_HANDOFF);
        assertThat(response.ineligibilityReasons())
                .containsExactly(
                        CapabilityIneligibilityReason.AUTHORIZATION_REQUIRED,
                        CapabilityIneligibilityReason.FALLBACK_SELECTED
                );
        assertThat(response.capabilities())
                .extracting(CheckoutResponse.CapabilityDecisionResponse::operation)
                .containsExactly(CommerceOperation.values());
        assertThat(response.nativeCheckoutEnabled()).isFalse();
    }
}
