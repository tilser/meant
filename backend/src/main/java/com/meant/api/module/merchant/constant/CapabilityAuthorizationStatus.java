package com.meant.api.module.merchant.constant;

public enum CapabilityAuthorizationStatus {
    NOT_REQUIRED,
    READY,
    NOT_AUTHORIZED,
    AUTHENTICATION_DISABLED,
    TIER_NOT_GRANTED,
    MISSING_SCOPES,
    UNSUPPORTED
}
