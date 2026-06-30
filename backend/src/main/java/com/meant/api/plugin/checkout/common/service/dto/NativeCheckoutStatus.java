package com.meant.api.plugin.checkout.common.service.dto;

public enum NativeCheckoutStatus {
    COMPLETED,
    PROCESSING,
    SCA_REQUIRED,
    CANCELED,
    HANDOFF_FALLBACK,
    RECOVERABLE_ERROR,
    UNRECOVERABLE_ERROR,
    SECURITY_LOCKED
}
