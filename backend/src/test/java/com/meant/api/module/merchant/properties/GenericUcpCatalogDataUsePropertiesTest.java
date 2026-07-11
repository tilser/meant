package com.meant.api.module.merchant.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class GenericUcpCatalogDataUsePropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "commerce.catalog.data-use.generic-ucp.search-cache-ttl=24h",
                    "commerce.catalog.data-use.generic-ucp.rehydrated-facts-ttl=2m"
            );

    @Test
    void bindsPositiveDurations() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            GenericUcpCatalogDataUseProperties properties = context.getBean(
                    GenericUcpCatalogDataUseProperties.class
            );
            assertThat(properties.searchCacheTtl()).isEqualTo(Duration.ofHours(24));
            assertThat(properties.rehydratedFactsTtl()).isEqualTo(Duration.ofMinutes(2));
        });
    }

    @Test
    void rejectsZeroOrNegativeDurationsAtStartup() {
        for (String property : Set.of(
                "commerce.catalog.data-use.generic-ucp.search-cache-ttl",
                "commerce.catalog.data-use.generic-ucp.rehydrated-facts-ttl"
        )) {
            for (String value : Set.of("0s", "-1s")) {
                contextRunner.withPropertyValues(property + "=" + value)
                        .run(context -> assertThat(context).hasFailed());
            }
        }
    }

    @Configuration
    @EnableConfigurationProperties(GenericUcpCatalogDataUseProperties.class)
    static class TestConfiguration {
    }
}
