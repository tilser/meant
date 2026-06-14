package com.meant.api.module.merchant.service.dto;

public record MerchantMcpProfileResult(
        String endpoint,
        StorePolicyFaqEntry entry
) {
}
