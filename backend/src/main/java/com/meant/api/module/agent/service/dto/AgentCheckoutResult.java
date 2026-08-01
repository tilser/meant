package com.meant.api.module.agent.service.dto;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import java.util.List;
import java.util.UUID;

public record AgentCheckoutResult(
        List<Checkout> checkouts,
        List<Failure> failures
) {

    public AgentCheckoutResult {
        checkouts = checkouts == null ? List.of() : List.copyOf(checkouts);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public static AgentCheckoutResult success(List<CheckoutResult> results) {
        return new AgentCheckoutResult(results.stream().map(Checkout::from).toList(), List.of());
    }

    public record Checkout(
            UUID cartId,
            UUID checkoutAttemptId,
            String status,
            String checkoutUrl,
            String continueUrl,
            Long totalAmountMinor,
            String currency,
            CheckoutNextAction nextAction,
            CommerceExecutionRail selectedRail,
            boolean embeddedCheckoutAvailable,
            boolean savedCheckoutDetailsAvailable,
            boolean savedCheckoutDetailsAutoApplied,
            List<Message> messages
    ) {
        public Checkout {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        public static Checkout from(CheckoutResult result) {
            return from(result, false);
        }

        public static Checkout from(CheckoutResult result, boolean savedCheckoutDetailsAutoApplied) {
            return new Checkout(
                    result.cartId(),
                    result.checkoutAttemptId(),
                    result.status(),
                    result.checkoutUrl(),
                    result.continueUrl(),
                    result.totalAmountMinor(),
                    result.currency(),
                    result.nextAction(),
                    result.selectedRail(),
                    result.embeddedCheckout() != null,
                    result.savedCheckoutDetails() != null,
                    savedCheckoutDetailsAutoApplied,
                    result.messages().stream().limit(20).map(Message::from).toList()
            );
        }
    }

    public record Message(String code, String severity, String content, String path) {
        public static Message from(CheckoutResult.Message result) {
            return new Message(result.code(), result.severity(), result.content(), result.path());
        }
    }

    public record Failure(UUID cartId, String safeMessage) {
    }
}
