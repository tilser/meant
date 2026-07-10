package com.meant.api.module.merchant.service.query;

import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GetMerchantIntegrationByProviderIdentityQuery(
        @NotNull MerchantIntegrationProvider provider,
        @NotBlank @Size(max = 512) String externalMerchantId
) {
}
