package com.meant.api.module.merchant.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
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
        Integer readTimeoutMilliseconds,

        @Positive
        @NotNull
        Integer merchantTimeoutMilliseconds,

        @NotNull
        Duration toolsListCacheTtl
) {

    @AssertTrue(message = "toolsListCacheTtl must be positive")
    public boolean isToolsListCacheTtlPositive() {
        return toolsListCacheTtl != null && !toolsListCacheTtl.isZero() && !toolsListCacheTtl.isNegative();
    }
}
