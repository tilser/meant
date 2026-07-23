package com.meant.api.module.review.service.task;

import com.meant.api.module.review.properties.ReviewProviderDiscoveryProperties;
import com.meant.api.module.review.service.ReviewProviderDiscoveryService;
import com.meant.api.module.review.service.command.DiscoverReviewProvidersCommand;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReviewProviderDiscoveryTask {

    private final ReviewProviderDiscoveryService reviewProviderDiscoveryService;
    private final ReviewProviderDiscoveryProperties reviewProviderDiscoveryProperties;

    public ReviewProviderDiscoveryTask(
            @Lazy ReviewProviderDiscoveryService reviewProviderDiscoveryService,
            ReviewProviderDiscoveryProperties reviewProviderDiscoveryProperties
    ) {
        this.reviewProviderDiscoveryService = reviewProviderDiscoveryService;
        this.reviewProviderDiscoveryProperties = reviewProviderDiscoveryProperties;
    }

    @Scheduled(fixedDelayString = "${review.provider-discovery.fixed-delay}")
    public void discoverProviders() {
        reviewProviderDiscoveryService.discoverProviders(new DiscoverReviewProvidersCommand(
                reviewProviderDiscoveryProperties.batchSize()
        ));
    }
}
