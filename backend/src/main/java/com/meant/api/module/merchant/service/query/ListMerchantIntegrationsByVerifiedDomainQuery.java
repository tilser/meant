package com.meant.api.module.merchant.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ListMerchantIntegrationsByVerifiedDomainQuery(
        @NotBlank
        @Size(max = 253)
        @Pattern(
                regexp = "(?i)^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+"
                        + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.?$"
        )
        String verifiedDomain
) {
}
