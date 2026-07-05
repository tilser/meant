package com.meant.api.plugin.checkout.common.service.dto;

import com.meant.api.plugin.checkout.common.entity.CheckoutCanaryOutcome;

public record NativeCheckoutInterpretation(
        NativeCheckoutResult result,
        CheckoutCanaryOutcome canaryOutcome,
        String remoteStatus,
        String errorCode,
        boolean chargeMismatch
) {
}
