package com.meant.api.module.cart.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutResultTest {

    @Test
    void recoverableMessageTakesPriorityBeforeBuyerHandoff() {
        CheckoutResult result = checkout(
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
        CheckoutResult result = checkout(
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

    private CheckoutResult checkout(String status, List<CheckoutResult.Message> messages) {
        return new CheckoutResult(
                UUID.randomUUID(),
                "cart-1",
                "checkout-1",
                status,
                null,
                "https://merchant.example/continue",
                "2026-04-08",
                1000L,
                "USD",
                messages,
                true
        );
    }

    private CheckoutResult.Message message(String code, String severity) {
        return new CheckoutResult.Message("error", code, severity, code, null);
    }
}
