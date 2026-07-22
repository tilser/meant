package com.meant.api.module.cart.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.user.service.dto.UserCheckoutDetailsResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutResponseTest {

    @Test
    void mapsExplicitExecutionContractAndDeprecatedCompatibilityView() {
        MerchantExecutionPolicy policy = MerchantExecutionPolicy.unavailable();
        UUID checkoutAttemptId = UUID.randomUUID();
        CheckoutResult result = new CheckoutResult(
                UUID.randomUUID(),
                "remote-cart",
                "checkout-1",
                checkoutAttemptId,
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
                policy,
                null
        );

        CheckoutResponse response = CheckoutResponse.from(result);

        assertThat(response.checkoutAttemptId()).isEqualTo(checkoutAttemptId);
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

    @Test
    void mapsSavedCheckoutDetailsIncludingNullableContactAndAddressFields() {
        UUID userId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-07-22T10:15:30Z");
        UserCheckoutDetailsResult savedDetails = new UserCheckoutDetailsResult(
                userId,
                "ada@example.com",
                "Ada",
                "Lovelace",
                null,
                "12 St James's Square",
                null,
                "London",
                null,
                "SW1Y 4LB",
                "GB",
                updatedAt
        );
        CheckoutResult result = new CheckoutResult(
                UUID.randomUUID(),
                "remote-cart",
                "checkout-1",
                UUID.randomUUID(),
                "incomplete",
                null,
                null,
                "2026-04-08",
                1200L,
                "GBP",
                List.of(),
                CheckoutNextAction.UPDATE_CHECKOUT,
                CommerceExecutionRail.NONE,
                List.of(),
                MerchantExecutionPolicy.unavailable(),
                null,
                savedDetails
        );

        CheckoutResponse response = CheckoutResponse.from(result);

        assertThat(response.savedCheckoutDetails()).isNotNull();
        assertThat(response.savedCheckoutDetails().updatedAt()).isEqualTo(updatedAt);
        assertThat(response.savedCheckoutDetails().buyer()).satisfies(buyer -> {
            assertThat(buyer.email()).isEqualTo("ada@example.com");
            assertThat(buyer.firstName()).isEqualTo("Ada");
            assertThat(buyer.lastName()).isEqualTo("Lovelace");
            assertThat(buyer.phoneNumber()).isNull();
        });
        assertThat(response.savedCheckoutDetails().shippingAddress()).satisfies(address -> {
            assertThat(address.streetAddress()).isEqualTo("12 St James's Square");
            assertThat(address.extendedAddress()).isNull();
            assertThat(address.addressLocality()).isEqualTo("London");
            assertThat(address.addressRegion()).isNull();
            assertThat(address.postalCode()).isEqualTo("SW1Y 4LB");
            assertThat(address.addressCountry()).isEqualTo("GB");
        });
    }
}
