package com.meant.api.module.merchant.constant;

public enum CapabilityIntegrationHealth {
    HEALTHY,
    PENDING,
    INACTIVE,
    SUSPENDED,
    REVOKED,
    NO_INTEGRATION;

    public static CapabilityIntegrationHealth from(MerchantIntegrationStatus status) {
        if (status == null) {
            return NO_INTEGRATION;
        }
        return switch (status) {
            case ACTIVE -> HEALTHY;
            case PENDING -> PENDING;
            case INACTIVE -> INACTIVE;
            case SUSPENDED -> SUSPENDED;
            case REVOKED -> REVOKED;
        };
    }
}
