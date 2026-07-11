package com.meant.api.module.catalog.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Owns the one lightweight application-wide scheduler used for discovery deadlines. */
@Configuration
class FederatedCatalogExecutorConfiguration {

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService catalogDiscoveryDeadlineScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
                1,
                Thread.ofPlatform().name("catalog-discovery-deadlines-", 0).daemon(true).factory()
        );
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
