package com.meant.api.plugin.checkout.common.entity;

public enum CheckoutIdempotencyStatus {
    RESERVED,
    COMPLETION_IN_FLIGHT,
    COMPLETED,
    FAILED
}
