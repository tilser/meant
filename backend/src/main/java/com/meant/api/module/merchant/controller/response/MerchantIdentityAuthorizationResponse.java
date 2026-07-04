package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantIdentityAuthorizationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record MerchantIdentityAuthorizationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String authorizationUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
