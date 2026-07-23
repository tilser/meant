package com.meant.api.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.meant.api.module.merchant.properties.MerchantEmbeddingProperties;
import com.meant.api.module.merchant.properties.MerchantEnrichmentProperties;
import com.meant.api.module.merchant.service.MerchantEnrichmentService;
import com.meant.api.module.merchant.service.MerchantRetrievalEmbeddingService;
import com.meant.api.module.merchant.service.UcpMerchantImportService;
import com.meant.api.module.merchant.service.task.MerchantRetrievalEmbeddingTask;
import com.meant.api.module.merchant.service.task.UcpMerchantEnrichmentTask;
import com.meant.api.module.merchant.service.task.UcpMerchantImportTask;
import com.meant.api.module.review.properties.ReviewProviderDiscoveryProperties;
import com.meant.api.module.review.service.ReviewProviderDiscoveryService;
import com.meant.api.module.review.service.task.ReviewProviderDiscoveryTask;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;

class ScheduledTaskLazyInitializationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void jobOnlyServicesAreDeclaredLazy() {
        assertThat(UcpMerchantImportService.class).hasAnnotation(Lazy.class);
        assertThat(MerchantEnrichmentService.class).hasAnnotation(Lazy.class);
        assertThat(MerchantRetrievalEmbeddingService.class).hasAnnotation(Lazy.class);
        assertThat(ReviewProviderDiscoveryService.class).hasAnnotation(Lazy.class);
    }

    @Test
    void jobOnlyServiceGraphsAreNotCreatedDuringContextRefresh() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(UcpMerchantImportTask.class);
            assertThat(context).hasSingleBean(UcpMerchantEnrichmentTask.class);
            assertThat(context).hasSingleBean(MerchantRetrievalEmbeddingTask.class);
            assertThat(context).hasSingleBean(ReviewProviderDiscoveryTask.class);

            ServiceCreations creations = context.getBean(ServiceCreations.class);
            assertThat(creations.total()).isZero();
        });
    }

    @Test
    void scheduledInvocationCreatesItsServiceGraphOnDemand() {
        contextRunner.run(context -> {
            ServiceCreations creations = context.getBean(ServiceCreations.class);

            context.getBean(UcpMerchantImportTask.class).importMerchants();
            context.getBean(UcpMerchantEnrichmentTask.class).enrichMerchants();
            context.getBean(ReviewProviderDiscoveryTask.class).discoverProviders();

            assertThat(creations.imports()).hasValue(1);
            assertThat(creations.enrichments()).hasValue(1);
            assertThat(creations.reviews()).hasValue(1);
            assertThat(creations.embeddings()).hasValue(0);
        });
    }

    @Test
    void missingEmbeddingApiKeyDoesNotCreateTheEmbeddingServiceGraph() {
        contextRunner.run(context -> {
            context.getBean(MerchantRetrievalEmbeddingTask.class).generateRetrievalEmbeddings();

            assertThat(context.getBean(ServiceCreations.class).embeddings()).hasValue(0);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            UcpMerchantImportTask.class,
            UcpMerchantEnrichmentTask.class,
            MerchantRetrievalEmbeddingTask.class,
            ReviewProviderDiscoveryTask.class
    })
    static class TestConfiguration {

        @Bean
        ServiceCreations serviceCreations() {
            return new ServiceCreations();
        }

        @Bean
        @Lazy
        UcpMerchantImportService ucpMerchantImportService(ServiceCreations creations) {
            creations.imports().incrementAndGet();
            return mock(UcpMerchantImportService.class);
        }

        @Bean
        @Lazy
        MerchantEnrichmentService merchantEnrichmentService(ServiceCreations creations) {
            creations.enrichments().incrementAndGet();
            return mock(MerchantEnrichmentService.class);
        }

        @Bean
        @Lazy
        MerchantRetrievalEmbeddingService merchantRetrievalEmbeddingService(ServiceCreations creations) {
            creations.embeddings().incrementAndGet();
            return mock(MerchantRetrievalEmbeddingService.class);
        }

        @Bean
        @Lazy
        ReviewProviderDiscoveryService reviewProviderDiscoveryService(ServiceCreations creations) {
            creations.reviews().incrementAndGet();
            return mock(ReviewProviderDiscoveryService.class);
        }

        @Bean
        MerchantEmbeddingProperties merchantEmbeddingProperties() {
            return new MerchantEmbeddingProperties(
                    "https://voyage.example",
                    "",
                    "test-model",
                    "test-rerank-model",
                    1024,
                    5
            );
        }

        @Bean
        MerchantEnrichmentProperties merchantEnrichmentProperties() {
            return new MerchantEnrichmentProperties(50, 60_000L, Duration.ofDays(1));
        }

        @Bean
        ReviewProviderDiscoveryProperties reviewProviderDiscoveryProperties() {
            return new ReviewProviderDiscoveryProperties(
                    50,
                    Duration.ofHours(1),
                    Duration.ofSeconds(5),
                    2 * 1024 * 1024,
                    Duration.ofMinutes(5),
                    Duration.ofHours(1),
                    Duration.ofDays(1)
            );
        }
    }

    record ServiceCreations(
            AtomicInteger imports,
            AtomicInteger enrichments,
            AtomicInteger embeddings,
            AtomicInteger reviews
    ) {

        ServiceCreations() {
            this(new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger());
        }

        int total() {
            return imports.get() + enrichments.get() + embeddings.get() + reviews.get();
        }
    }
}
