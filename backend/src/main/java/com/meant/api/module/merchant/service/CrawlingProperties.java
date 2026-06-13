package com.meant.api.module.merchant.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "crawling")
@Validated
@Getter
@Setter
public class CrawlingProperties {

    @NotBlank
    private String ucpDatasetRowsUrl = "https://datasets-server.huggingface.co/rows?dataset=UCPChecker%2Fucp-merchants&config=default&split=train";

    @Positive
    private int ucpDatasetPageSize = 100;

    @NotBlank
    private String ucpDatasetImportCron = "0 0 3 2 * *";

    @NotBlank
    private String ucpDatasetImportZone = "UTC";
}
