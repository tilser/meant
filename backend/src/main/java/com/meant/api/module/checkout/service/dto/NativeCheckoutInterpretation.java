package com.meant.api.module.checkout.service.dto;

import com.meant.api.module.checkout.entity.CheckoutCanaryOutcome;

public record NativeCheckoutInterpretation(
        NativeCheckoutResult result,
        CheckoutCanaryOutcome canaryOutcome,
        String remoteStatus,
        String errorCode,
        boolean chargeMismatch
) {
}
