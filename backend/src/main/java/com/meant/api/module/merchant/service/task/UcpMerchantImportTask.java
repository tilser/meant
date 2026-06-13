package com.meant.api.module.merchant.service.task;

import com.meant.api.module.merchant.service.UcpMerchantImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UcpMerchantImportTask {

    private final UcpMerchantImportService ucpMerchantImportService;

    @Scheduled(
            cron = "${crawling.ucp-dataset-import-cron}",
            zone = "${crawling.ucp-dataset-import-zone}"
    )
    public void importMerchants() {
        ucpMerchantImportService.importMerchants();
    }
}
