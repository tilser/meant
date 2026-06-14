package com.meant.api.module.merchant.properties;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "merchant.import")
public record MerchantImportProperties(

        @NotNull
        List<String> excludedDomains
) {
}
