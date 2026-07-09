package com.meant.api.module.cart.service.dto;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record CheckoutResult(
        UUID cartId,
        String remoteCartId,
        String checkoutId,
        String status,
        String checkoutUrl,
        String continueUrl,
        String ucpVersion,
        Long totalAmountMinor,
        String currency,
        List<Message> messages,
        boolean nativeCheckoutEnabled
) {

    public CheckoutResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public CheckoutResult(UUID cartId, String remoteCartId, String checkoutUrl, String continueUrl) {
        this(cartId, remoteCartId, null, null, checkoutUrl, continueUrl, null, null, null, List.of(), false);
    }

    public boolean requiresEscalation() {
        return status != null && status.trim().equalsIgnoreCase("requires_escalation");
    }

    public CheckoutNextAction nextAction() {
        if (messages.stream().anyMatch(Message::recoverable)) {
            return CheckoutNextAction.UPDATE_CHECKOUT;
        }
        if (messages.stream().anyMatch(Message::requiresBuyerAction)) {
            return CheckoutNextAction.HANDOFF;
        }
        return switch (normalizedStatus()) {
            case "incomplete" -> CheckoutNextAction.UPDATE_CHECKOUT;
            case "requires_escalation" -> CheckoutNextAction.HANDOFF;
            case "ready_for_complete", "ready_for_payment" -> CheckoutNextAction.COMPLETE_CHECKOUT;
            case "complete_in_progress" -> CheckoutNextAction.WAIT;
            case "completed" -> CheckoutNextAction.DONE;
            case "canceled" -> CheckoutNextAction.RESTART;
            default -> CheckoutNextAction.UNKNOWN;
        };
    }

    private String normalizedStatus() {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    public record Message(
            String type,
            String code,
            String severity,
            String content,
            String path
    ) {

        private boolean recoverable() {
            return normalizedSeverity().equals("recoverable");
        }

        private boolean requiresBuyerAction() {
            return normalizedSeverity().equals("requires_buyer_input")
                    || normalizedSeverity().equals("requires_buyer_review");
        }

        private String normalizedSeverity() {
            return severity == null ? "" : severity.trim().toLowerCase(Locale.ROOT);
        }
    }
}
