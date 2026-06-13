package com.meant.api.module.merchant.service.task;

import com.meant.api.module.merchant.service.UcpMerchantImportService;
import com.meant.api.module.merchant.service.command.ImportUcpMerchantsCommand;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UcpMerchantImportTask {

    private final UcpMerchantImportService ucpMerchantImportService;

    public UcpMerchantImportTask(UcpMerchantImportService ucpMerchantImportService) {
        this.ucpMerchantImportService = ucpMerchantImportService;
    }

    @Scheduled(
            cron = "${crawling.ucp-dataset-import-cron}",
            zone = "${crawling.ucp-dataset-import-zone}"
    )
    public void importMerchants() {
        ucpMerchantImportService.importMerchants(new ImportUcpMerchantsCommand());
    }
}
