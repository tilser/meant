package com.meant.api.module.merchant.service.dto;

import java.util.List;
import java.util.UUID;

public record MerchantIdentityAuthorizationResult(
        UUID merchantId,
        String authorizationUrl,
        String state,
        List<String> scopes
) {
}
