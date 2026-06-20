package com.meant.api.module.merchant.controller.request;

import jakarta.validation.constraints.NotBlank;

public record MerchantIdentityCallbackRequest(
        @NotBlank String state,
        @NotBlank String code,
        String issuer
) {
}
