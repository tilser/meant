package com.meant.api.module.merchant.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record MerchantIdentityCallbackRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String code,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String issuer
) {
}
