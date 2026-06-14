package com.meant.api.module.merchant.service.task;

import com.meant.api.module.merchant.properties.MerchantEnrichmentProperties;
import com.meant.api.module.merchant.service.MerchantEnrichmentService;
import com.meant.api.module.merchant.service.command.EnrichMerchantsCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UcpMerchantEnrichmentTask {

    private final MerchantEnrichmentService merchantEnrichmentService;
    private final MerchantEnrichmentProperties merchantEnrichmentProperties;

    @Scheduled(fixedDelay = 60_000
    )
    public void enrichMerchants() {
        merchantEnrichmentService.enrichMerchants(
                new EnrichMerchantsCommand(
                        merchantEnrichmentProperties.batchSize()
                )
        );
    }
}
