package com.meant.api.plugin.checkout.common.service;

import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.plugin.checkout.common.entity.CheckoutCanaryOutcome;
import com.meant.api.plugin.checkout.common.service.dto.NativeCheckoutInterpretation;
import com.meant.api.plugin.checkout.common.service.dto.NativeCheckoutResult;
import com.meant.api.plugin.checkout.common.service.dto.NativeCheckoutStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NativeCheckoutResultInterpreter {

    public NativeCheckoutInterpretation completionResponse(UcpCheckoutToolResult result) {
        UcpCheckoutResponse.CheckoutMessage unrecoverable = firstUnrecoverableMessage(result.response());
        if (unrecoverable != null) {
            boolean chargeMismatch = chargeMismatch(unrecoverable);
            return new NativeCheckoutInterpretation(
                    new NativeCheckoutResult(
                            NativeCheckoutStatus.UNRECOVERABLE_ERROR,
                            checkoutId(result.response()),
                            null,
                            continueUrl(result.response()),
                            messages(result.response()),
                            true
                    ),
                    chargeMismatch ? CheckoutCanaryOutcome.CHARGE_MISMATCH : CheckoutCanaryOutcome.UNRECOVERABLE_ERROR,
                    status(result.response()),
                    unrecoverable.code(),
                    chargeMismatch
            );
        }

        NativeCheckoutInterpretation terminal = terminalStatusResult(result, true, true);
        if (terminal != null) {
            return terminal;
        }

        UcpCheckoutResponse.CheckoutMessage recoverable = firstRecoverableMessage(result.response());
        if (recoverable != null) {
            return new NativeCheckoutInterpretation(
                    new NativeCheckoutResult(
                            NativeCheckoutStatus.RECOVERABLE_ERROR,
                            checkoutId(result.response()),
                            null,
                            continueUrl(result.response()),
                            messages(result.response()),
                            true
                    ),
                    CheckoutCanaryOutcome.RECOVERABLE_ERROR,
                    status(result.response()),
                    recoverable.code(),
                    false
            );
        }

        return processingResult(result, true);
    }

    public NativeCheckoutInterpretation terminalStatusResult(
            UcpCheckoutToolResult result,
            boolean nativeAttempted,
            boolean allowSca
    ) {
        UcpCheckoutResponse response = result.response();
        String normalizedStatus = normalizedStatus(status(response));
        if ("completed".equals(normalizedStatus) || StringUtils.hasText(orderRef(response))) {
            return new NativeCheckoutInterpretation(
                    new NativeCheckoutResult(
                            NativeCheckoutStatus.COMPLETED,
                            checkoutId(response),
                            orderRef(response),
                            null,
                            messages(response),
                            nativeAttempted
                    ),
                    CheckoutCanaryOutcome.COMPLETED,
                    normalizedStatus,
                    null,
                    false
            );
        }
        if ("canceled".equals(normalizedStatus) || "cancelled".equals(normalizedStatus)) {
            return new NativeCheckoutInterpretation(
                    new NativeCheckoutResult(
                            NativeCheckoutStatus.CANCELED,
                            checkoutId(response),
                            null,
                            null,
                            messages(response),
                            nativeAttempted
                    ),
                    CheckoutCanaryOutcome.CANCELED,
                    normalizedStatus,
                    null,
                    false
            );
        }
        if (allowSca && "requires_escalation".equals(normalizedStatus) && StringUtils.hasText(continueUrl(response))) {
            return new NativeCheckoutInterpretation(
                    new NativeCheckoutResult(
                            NativeCheckoutStatus.SCA_REQUIRED,
                            checkoutId(response),
                            null,
                            continueUrl(response),
                            messages(response),
                            nativeAttempted
                    ),
                    CheckoutCanaryOutcome.SCA_REQUIRED,
                    normalizedStatus,
                    null,
                    false
            );
        }
        return null;
    }

    public NativeCheckoutInterpretation processingResult(UcpCheckoutToolResult result, boolean nativeAttempted) {
        return new NativeCheckoutInterpretation(
                new NativeCheckoutResult(
                        NativeCheckoutStatus.PROCESSING,
                        checkoutId(result.response()),
                        null,
                        continueUrl(result.response()),
                        messages(result.response()),
                        nativeAttempted
                ),
                CheckoutCanaryOutcome.PROCESSING,
                status(result.response()),
                null,
                false
        );
    }

    public NativeCheckoutInterpretation cancelResult(UcpCheckoutToolResult result) {
        NativeCheckoutInterpretation terminal = terminalStatusResult(result, true, true);
        if (terminal != null) {
            return terminal;
        }
        return new NativeCheckoutInterpretation(
                new NativeCheckoutResult(
                        NativeCheckoutStatus.CANCELED,
                        checkoutId(result.response()),
                        null,
                        null,
                        messages(result.response()),
                        true
                ),
                null,
                null,
                null,
                false
        );
    }

    private String status(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null && StringUtils.hasText(checkout.status())) {
            return checkout.status();
        }
        return response.status();
    }

    private String checkoutId(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null && StringUtils.hasText(checkout.id())) {
            return checkout.id();
        }
        return response.checkoutId();
    }

    private String continueUrl(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null && StringUtils.hasText(checkout.continueUrl())) {
            return checkout.continueUrl();
        }
        return response.continueUrl();
    }

    private String orderRef(UcpCheckoutResponse response) {
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        String orderRef = checkout == null ? null : checkout.resolvedOrderRef();
        return NativeCheckoutValueSupport.firstText(
                orderRef,
                response.orderId(),
                response.order() == null ? null : response.order().resolvedRef()
        );
    }

    private UcpCheckoutResponse.CheckoutMessage firstRecoverableMessage(UcpCheckoutResponse response) {
        return allMessages(response).stream()
                .filter(UcpCheckoutResponse.CheckoutMessage::isRecoverable)
                .findFirst()
                .orElse(null);
    }

    private UcpCheckoutResponse.CheckoutMessage firstUnrecoverableMessage(UcpCheckoutResponse response) {
        return allMessages(response).stream()
                .filter(UcpCheckoutResponse.CheckoutMessage::isUnrecoverable)
                .findFirst()
                .orElse(null);
    }

    private List<String> messages(UcpCheckoutResponse response) {
        return allMessages(response).stream()
                .map(UcpCheckoutResponse.CheckoutMessage::message)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<UcpCheckoutResponse.CheckoutMessage> allMessages(UcpCheckoutResponse response) {
        List<UcpCheckoutResponse.CheckoutMessage> values = new ArrayList<>();
        if (response.messages() != null) {
            values.addAll(response.messages());
        }
        UcpCheckoutResponse.Checkout checkout = response.resolvedCheckout();
        if (checkout != null && checkout.messages() != null) {
            values.addAll(checkout.messages());
        }
        return values;
    }

    private boolean chargeMismatch(UcpCheckoutResponse.CheckoutMessage message) {
        return matches(message.code(), "charge_mismatch") || matches(message.code(), "amount_mismatch");
    }

    private String normalizedStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(String value, String expected) {
        return value != null && value.trim().equalsIgnoreCase(expected);
    }
}
