package com.meant.api.module.checkout.constant;

import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import java.util.Locale;
import java.util.stream.Stream;

/** Stable local lifecycle classification; unrecognized provider states remain UNKNOWN. */
public enum CheckoutLifecycleState {
    INCOMPLETE,
    REQUIRES_ESCALATION,
    READY_FOR_COMPLETE,
    PROCESSING,
    COMPLETED,
    CANCELLED,
    RECOVERABLE_FAILURE,
    TERMINAL_FAILURE,
    UNKNOWN;

    public static CheckoutLifecycleState from(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response == null ? null : response.resolvedCheckout();
        if (hasTerminalFailure(response, checkout)) {
            return TERMINAL_FAILURE;
        }
        if (hasRecoverableFailure(response, checkout)) {
            return RECOVERABLE_FAILURE;
        }
        String status = checkout == null || checkout.status() == null
                ? "" : checkout.status().trim().toLowerCase(Locale.ROOT);
        return switch (status) {
            case "incomplete" -> INCOMPLETE;
            case "requires_escalation" -> REQUIRES_ESCALATION;
            case "ready_for_complete", "ready_for_payment" -> READY_FOR_COMPLETE;
            case "complete_in_progress", "processing" -> PROCESSING;
            case "completed" -> COMPLETED;
            case "canceled", "cancelled" -> CANCELLED;
            default -> UNKNOWN;
        };
    }

    private static boolean hasTerminalFailure(
            UcpCheckoutResponse response, UcpCheckoutResponse.Checkout checkout) {
        return messages(response, checkout)
                .anyMatch(message -> message.isError() && !message.isRecoverable() && !message.requiresBuyerAction())
                || response != null && response.errors().stream()
                .anyMatch(error -> error != null && !error.isRecoverable());
    }

    private static boolean hasRecoverableFailure(
            UcpCheckoutResponse response, UcpCheckoutResponse.Checkout checkout) {
        return messages(response, checkout).anyMatch(UcpCheckoutResponse.CheckoutMessage::isRecoverable)
                || response != null && response.errors().stream()
                .anyMatch(error -> error != null && error.isRecoverable());
    }

    private static Stream<UcpCheckoutResponse.CheckoutMessage> messages(
            UcpCheckoutResponse response, UcpCheckoutResponse.Checkout checkout) {
        if (response == null) {
            return Stream.empty();
        }
        return Stream.concat(response.messages().stream(),
                        checkout == null ? Stream.empty() : checkout.messages().stream())
                .filter(message -> message != null);
    }
}
