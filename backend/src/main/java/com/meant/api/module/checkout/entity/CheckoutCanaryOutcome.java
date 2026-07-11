package com.meant.api.module.checkout.entity;

public enum CheckoutCanaryOutcome {
    COMPLETED,
    PROCESSING,
    SCA_REQUIRED,
    RECOVERABLE_ERROR,
    UNRECOVERABLE_ERROR,
    PROTOCOL_ERROR,
    CANCELED,
    CHARGE_MISMATCH,
    HANDOFF_FALLBACK
}
