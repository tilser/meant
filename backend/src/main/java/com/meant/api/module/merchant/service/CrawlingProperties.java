package com.meant.api.module.merchant.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "crawling")
@Validated
public class CrawlingProperties {

    @NotBlank
    private String ucpDatasetRowsUrl = "https://datasets-server.huggingface.co/rows?dataset=UCPChecker%2Fucp-merchants&config=default&split=train";

    @Positive
    private int ucpDatasetPageSize = 100;

    @NotBlank
    private String ucpDatasetImportCron = "0 0 3 2 * *";

    @NotBlank
    private String ucpDatasetImportZone = "UTC";

    public String getUcpDatasetRowsUrl() {
        return ucpDatasetRowsUrl;
    }

    public void setUcpDatasetRowsUrl(String ucpDatasetRowsUrl) {
        this.ucpDatasetRowsUrl = ucpDatasetRowsUrl;
    }

    public int getUcpDatasetPageSize() {
        return ucpDatasetPageSize;
    }

    public void setUcpDatasetPageSize(int ucpDatasetPageSize) {
        this.ucpDatasetPageSize = ucpDatasetPageSize;
    }

    public String getUcpDatasetImportCron() {
        return ucpDatasetImportCron;
    }

    public void setUcpDatasetImportCron(String ucpDatasetImportCron) {
        this.ucpDatasetImportCron = ucpDatasetImportCron;
    }

    public String getUcpDatasetImportZone() {
        return ucpDatasetImportZone;
    }

    public void setUcpDatasetImportZone(String ucpDatasetImportZone) {
        this.ucpDatasetImportZone = ucpDatasetImportZone;
    }
}
