package com.meant.api.module.cart.service;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.service.dto.CheckoutExecutionPlan;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CheckoutExecutionPlanner {

    public CheckoutExecutionPlan resolve(
            String status,
            List<CheckoutResult.Message> messages,
            MerchantExecutionPolicy policy
    ) {
        List<CheckoutResult.Message> checkoutMessages = messages == null ? List.of() : messages;
        MerchantExecutionPolicy executionPolicy = policy == null ? MerchantExecutionPolicy.unavailable() : policy;
        if (normalize(status).equals("merchant_handoff_required")) {
            return merchantRedirectPlan(executionPolicy);
        }
        if (checkoutMessages.stream().anyMatch(message -> message.recoverable()
                && !isExtensionInteraction(message))) {
            return checkoutSessionPlan(CheckoutNextAction.UPDATE_CHECKOUT, executionPolicy);
        }
        if (checkoutMessages.stream().anyMatch(CheckoutResult.Message::requiresBuyerAction)) {
            return escalationPlan(executionPolicy);
        }
        return switch (normalize(status)) {
            case "incomplete" -> checkoutSessionPlan(CheckoutNextAction.UPDATE_CHECKOUT, executionPolicy);
            case "requires_escalation" -> escalationPlan(executionPolicy);
            case "ready_for_complete", "ready_for_payment" -> paymentPlan(executionPolicy);
            case "complete_in_progress", "processing" -> new CheckoutExecutionPlan(
                    CheckoutNextAction.WAIT,
                    CommerceExecutionRail.DIRECT_CHECKOUT_COMPLETION,
                    List.of()
            );
            case "completed" -> terminalPlan(CheckoutNextAction.DONE);
            case "canceled", "cancelled", "terminal_failure" -> terminalPlan(CheckoutNextAction.RESTART);
            case "recoverable_failure" -> checkoutSessionPlan(CheckoutNextAction.UPDATE_CHECKOUT, executionPolicy);
            default -> checkoutSessionPlan(CheckoutNextAction.UNKNOWN, executionPolicy);
        };
    }

    private boolean isExtensionInteraction(CheckoutResult.Message message) {
        return message != null && "extension_interaction_required".equalsIgnoreCase(
                message.code() == null ? "" : message.code().trim());
    }

    private CheckoutExecutionPlan escalationPlan(MerchantExecutionPolicy policy) {
        CommerceCapabilityDecision embedded = policy.decision(CommerceOperation.EMBEDDED_CHECKOUT);
        if (embedded.available()) {
            return new CheckoutExecutionPlan(
                    CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT,
                    embedded.selectedRail(),
                    List.of()
            );
        }
        List<CapabilityIneligibilityReason> reasons = embedded.ineligibilityReasons().isEmpty()
                ? List.of(CapabilityIneligibilityReason.FALLBACK_SELECTED)
                : embedded.ineligibilityReasons();
        return handoffPlan(reasons);
    }

    private CheckoutExecutionPlan checkoutSessionPlan(
            CheckoutNextAction action,
            MerchantExecutionPolicy policy
    ) {
        CommerceCapabilityDecision session = policy.decision(CommerceOperation.CHECKOUT_SESSION);
        return new CheckoutExecutionPlan(
                action,
                session.available() ? session.selectedRail() : CommerceExecutionRail.NONE,
                session.available() ? List.of() : session.ineligibilityReasons()
        );
    }

    private CheckoutExecutionPlan paymentPlan(MerchantExecutionPolicy policy) {
        CommerceCapabilityDecision embedded = policy.decision(CommerceOperation.EMBEDDED_CHECKOUT);
        if (embedded.available()) {
            return new CheckoutExecutionPlan(
                    CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT,
                    embedded.selectedRail(),
                    List.of()
            );
        }
        CommerceCapabilityDecision direct = policy.decision(CommerceOperation.DIRECT_CHECKOUT_COMPLETION);
        if (direct.available()) {
            return new CheckoutExecutionPlan(
                    CheckoutNextAction.COMPLETE_CHECKOUT,
                    direct.selectedRail(),
                    List.of()
            );
        }
        Set<CapabilityIneligibilityReason> reasons = new LinkedHashSet<>();
        reasons.addAll(embedded.ineligibilityReasons());
        reasons.addAll(direct.ineligibilityReasons());
        reasons.add(CapabilityIneligibilityReason.FALLBACK_SELECTED);
        return handoffPlan(List.copyOf(reasons));
    }

    private CheckoutExecutionPlan handoffPlan(List<CapabilityIneligibilityReason> reasons) {
        return new CheckoutExecutionPlan(
                CheckoutNextAction.HANDOFF,
                CommerceExecutionRail.MERCHANT_HANDOFF,
                reasons
        );
    }

    private CheckoutExecutionPlan merchantRedirectPlan(MerchantExecutionPolicy policy) {
        CommerceCapabilityDecision embedded = policy.decision(CommerceOperation.EMBEDDED_CHECKOUT);
        if (embedded.available()) {
            return new CheckoutExecutionPlan(
                    CheckoutNextAction.OPEN_EMBEDDED_CHECKOUT,
                    embedded.selectedRail(),
                    List.of()
            );
        }
        Set<CapabilityIneligibilityReason> reasons = new LinkedHashSet<>();
        reasons.add(CapabilityIneligibilityReason.MERCHANT_REDIRECT_REQUIRED);
        reasons.addAll(embedded.ineligibilityReasons());
        reasons.add(CapabilityIneligibilityReason.FALLBACK_SELECTED);
        return handoffPlan(List.copyOf(reasons));
    }

    private CheckoutExecutionPlan terminalPlan(CheckoutNextAction action) {
        return new CheckoutExecutionPlan(action, CommerceExecutionRail.NONE, List.of());
    }

    private String normalize(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }
}
