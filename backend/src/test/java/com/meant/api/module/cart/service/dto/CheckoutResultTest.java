package com.meant.api.module.cart.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.service.CheckoutExecutionPlanner;
import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutResultTest {

    @Test
    void recoverableMessageTakesPriorityBeforeBuyerHandoff() {
        var result = checkout(
                "requires_escalation",
                List.of(
                        message("extension_interaction_required", "requires_buyer_input"),
                        message("delivery_address_required", "recoverable")
                )
        );

        assertThat(result.nextAction()).isEqualTo(CheckoutNextAction.UPDATE_CHECKOUT);
    }

    @Test
    void extensionInteractionRequiringBuyerInputHandsOffAfterRecoverableErrorsAreResolved() {
        var result = checkout(
                "requires_escalation",
                List.of(message("extension_interaction_required", "requires_buyer_input"))
        );

        assertThat(result.nextAction()).isEqualTo(CheckoutNextAction.HANDOFF);
    }

    @Test
    void statusDeterminesActionWhenMessagesDoNotProvideOne() {
        assertThat(checkout("incomplete", List.of()).nextAction())
                .isEqualTo(CheckoutNextAction.UPDATE_CHECKOUT);
        assertThat(checkout("requires_escalation", List.of()).nextAction())
                .isEqualTo(CheckoutNextAction.HANDOFF);
        assertThat(checkout("ready_for_complete", List.of()).nextAction())
                .isEqualTo(CheckoutNextAction.COMPLETE_CHECKOUT);
    }

    @Test
    void embeddedCheckoutIsSelectedBeforeUnavailableDirectCompletion() {
        var result = new CheckoutExecutionPlanner().resolve(
                "ready_for_complete",
                List.of(),
                availablePolicy(CommerceOperation.EMBEDDED_CHECKOUT, CommerceExecutionRail.EMBEDDED_CHECKOUT)
        );

        assertThat(result.nextAction()).isEqualTo(CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT);
        assertThat(result.selectedRail()).isEqualTo(CommerceExecutionRail.EMBEDDED_CHECKOUT);
    }

    @Test
    void embeddedCheckoutHandlesRequiresEscalationWithoutTopLevelNavigation() {
        var result = new CheckoutExecutionPlanner().resolve(
                "requires_escalation",
                List.of(message("extension_interaction_required", "requires_buyer_input")),
                availablePolicy(CommerceOperation.EMBEDDED_CHECKOUT, CommerceExecutionRail.EMBEDDED_CHECKOUT)
        );

        assertThat(result.nextAction()).isEqualTo(CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT);
        assertThat(result.selectedRail()).isEqualTo(CommerceExecutionRail.EMBEDDED_CHECKOUT);
    }

    @Test
    void recoverableExtensionInteractionOpensEmbeddedCheckoutInsteadOfRepeatingUcpUpdate() {
        var result = new CheckoutExecutionPlanner().resolve(
                "requires_escalation",
                List.of(message("extension_interaction_required", "recoverable")),
                availablePolicy(CommerceOperation.EMBEDDED_CHECKOUT, CommerceExecutionRail.EMBEDDED_CHECKOUT)
        );

        assertThat(result.nextAction()).isEqualTo(CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT);
    }

    @Test
    void embeddedCheckoutTakesPriorityOverProviderRedirectHandoff() {
        var result = new CheckoutExecutionPlanner().resolve(
                "MERCHANT_HANDOFF_REQUIRED",
                List.of(
                        message("item_unavailable", "recoverable"),
                        message("redirect_to_checkout_required", "requires_buyer_input")
                ),
                availablePolicy(CommerceOperation.EMBEDDED_CHECKOUT, CommerceExecutionRail.EMBEDDED_CHECKOUT)
        );

        assertThat(result.nextAction()).isEqualTo(CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT);
        assertThat(result.selectedRail()).isEqualTo(CommerceExecutionRail.EMBEDDED_CHECKOUT);
        assertThat(result.ineligibilityReasons()).isEmpty();
    }

    @Test
    void providerRedirectHandsOffOnlyWhenEmbeddedCheckoutIsUnavailable() {
        var result = new CheckoutExecutionPlanner().resolve(
                "MERCHANT_HANDOFF_REQUIRED",
                List.of(
                        message("item_unavailable", "recoverable"),
                        message("redirect_to_checkout_required", "requires_buyer_input")
                ),
                MerchantExecutionPolicy.unavailable()
        );

        assertThat(result.nextAction()).isEqualTo(CheckoutNextAction.HANDOFF);
        assertThat(result.selectedRail()).isEqualTo(CommerceExecutionRail.MERCHANT_HANDOFF);
        assertThat(result.ineligibilityReasons()).contains(
                CapabilityIneligibilityReason.MERCHANT_REDIRECT_REQUIRED,
                CapabilityIneligibilityReason.FALLBACK_SELECTED
        );
    }

    private com.meant.api.module.cart.service.dto.CheckoutExecutionPlan checkout(
            String status,
            List<CheckoutResult.Message> messages
    ) {
        return new CheckoutExecutionPlanner().resolve(
                status,
                messages,
                availablePolicy(
                        CommerceOperation.DIRECT_CHECKOUT_COMPLETION,
                        CommerceExecutionRail.DIRECT_CHECKOUT_COMPLETION
                )
        );
    }

    private CheckoutResult.Message message(String code, String severity) {
        return new CheckoutResult.Message("error", code, severity, code, null);
    }

    private MerchantExecutionPolicy availablePolicy(
            CommerceOperation availableOperation,
            CommerceExecutionRail rail
    ) {
        return new MerchantExecutionPolicy(Arrays.stream(CommerceOperation.values())
                .map(operation -> operation == availableOperation
                        ? availableDecision(operation, rail)
                        : MerchantExecutionPolicy.unavailable().decision(operation))
                .toList());
    }

    private CommerceCapabilityDecision availableDecision(
            CommerceOperation operation,
            CommerceExecutionRail rail
    ) {
        return new CommerceCapabilityDecision(
                operation,
                true,
                CapabilityAuthorizationDecision.notRequired(),
                true,
                CapabilityIntegrationHealth.HEALTHY,
                true,
                CapabilityAvailability.AVAILABLE,
                rail,
                List.of(),
                UUID.randomUUID(),
                null
        );
    }
}
