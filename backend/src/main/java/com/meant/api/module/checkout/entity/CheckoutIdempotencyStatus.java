package com.meant.api.module.checkout.entity;

public enum CheckoutIdempotencyStatus {
    RESERVED,
    COMPLETION_IN_FLIGHT,
    COMPLETED,
    FAILED
}
