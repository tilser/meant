package com.meant.api.plugin.checkout.common.service.dto;

import java.util.List;

public record NativeCheckoutResult(
        NativeCheckoutStatus status,
        String checkoutId,
        String orderRef,
        String continueUrl,
        List<String> messages,
        boolean nativeAttempted
) {

    public NativeCheckoutResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static NativeCheckoutResult handoff(String checkoutId, String continueUrl) {
        return new NativeCheckoutResult(
                NativeCheckoutStatus.HANDOFF_FALLBACK,
                checkoutId,
                null,
                continueUrl,
                List.of(),
                false
        );
    }
}
