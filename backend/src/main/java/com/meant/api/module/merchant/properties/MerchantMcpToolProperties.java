package com.meant.api.module.merchant.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "merchant.mcp-tool")
public record MerchantMcpToolProperties(

        @Positive
        @NotNull
        Integer connectTimeoutMilliseconds,

        @Positive
        @NotNull
        Integer readTimeoutMilliseconds
) {
}
