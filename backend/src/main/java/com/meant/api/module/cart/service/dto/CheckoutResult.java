package com.meant.api.module.cart.service.dto;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
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
        CheckoutNextAction nextAction,
        CommerceExecutionRail selectedRail,
        List<CapabilityIneligibilityReason> ineligibilityReasons,
        MerchantExecutionPolicy executionPolicy
) {

    public CheckoutResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
        ineligibilityReasons = ineligibilityReasons == null ? List.of() : List.copyOf(ineligibilityReasons);
        executionPolicy = executionPolicy == null ? MerchantExecutionPolicy.unavailable() : executionPolicy;
    }

    public CheckoutResult(UUID cartId, String remoteCartId, String checkoutUrl, String continueUrl) {
        this(
                cartId,
                remoteCartId,
                null,
                null,
                checkoutUrl,
                continueUrl,
                null,
                null,
                null,
                List.of(),
                CheckoutNextAction.UNKNOWN,
                CommerceExecutionRail.NONE,
                List.of(),
                MerchantExecutionPolicy.unavailable()
        );
    }

    public boolean requiresEscalation() {
        return status != null && status.trim().equalsIgnoreCase("requires_escalation");
    }

    public record Message(
            String type,
            String code,
            String severity,
            String content,
            String path
    ) {

        public boolean recoverable() {
            return normalizedSeverity().equals("recoverable");
        }

        public boolean requiresBuyerAction() {
            return normalizedSeverity().equals("requires_buyer_input")
                    || normalizedSeverity().equals("requires_buyer_review");
        }

        private String normalizedSeverity() {
            return severity == null ? "" : severity.trim().toLowerCase(Locale.ROOT);
        }
    }
}
