package com.meant.api.module.merchant.service.task;

import com.meant.api.module.merchant.properties.MerchantEnrichmentProperties;
import com.meant.api.module.merchant.service.MerchantEnrichmentService;
import com.meant.api.module.merchant.service.command.EnrichMerchantsCommand;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UcpMerchantEnrichmentTask {

    private final MerchantEnrichmentService merchantEnrichmentService;
    private final MerchantEnrichmentProperties merchantEnrichmentProperties;

    public UcpMerchantEnrichmentTask(
            @Lazy MerchantEnrichmentService merchantEnrichmentService,
            MerchantEnrichmentProperties merchantEnrichmentProperties
    ) {
        this.merchantEnrichmentService = merchantEnrichmentService;
        this.merchantEnrichmentProperties = merchantEnrichmentProperties;
    }

    @Scheduled(fixedDelayString = "${merchant.enrichment.fixed-delay}")
    public void enrichMerchants() {
        merchantEnrichmentService.enrichMerchants(
                new EnrichMerchantsCommand(
                        merchantEnrichmentProperties.batchSize()
                )
        );
    }
}
