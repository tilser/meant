package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantIdentityAuthorizationResult;
import java.util.List;
import java.util.UUID;

public record MerchantIdentityAuthorizationResponse(
        UUID merchantId,
        String authorizationUrl,
        String state,
        List<String> scopes
) {

    public static MerchantIdentityAuthorizationResponse from(MerchantIdentityAuthorizationResult result) {
        return new MerchantIdentityAuthorizationResponse(
                result.merchantId(),
                result.authorizationUrl(),
                result.state(),
                result.scopes()
        );
    }
}
