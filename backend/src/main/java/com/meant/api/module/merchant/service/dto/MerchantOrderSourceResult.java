package com.meant.api.module.merchant.service.dto;

import java.util.UUID;

public record MerchantOrderSourceResult(
        UUID merchantId,
        String domain,
        String name
) {
}
