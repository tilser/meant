package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.CommerceExecutionRail;

public record CapabilityFallback(
        boolean supported,
        CommerceExecutionRail rail
) {

    public static CapabilityFallback unsupported() {
        return new CapabilityFallback(false, CommerceExecutionRail.NONE);
    }

    public static CapabilityFallback supported(CommerceExecutionRail rail) {
        return new CapabilityFallback(true, rail);
    }
}
