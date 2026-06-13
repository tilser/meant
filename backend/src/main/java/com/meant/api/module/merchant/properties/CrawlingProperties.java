package com.meant.api.module.merchant.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "crawling")
public record CrawlingProperties(

        @NotBlank
        String ucpDatasetRowsUrl,

        @Positive
        @NotNull
        Integer ucpDatasetPageSize,

        @NotBlank
        String ucpDatasetImportCron,

        @NotBlank
        String ucpDatasetImportZone
) {
}
