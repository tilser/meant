package com.meant.api.plugin.catalog.shopify;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ShopifyGlobalCatalogPropertiesTest {

    @Test
    void exposesGlobalCatalogDefaultsInTheApplicationEnvironment() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.config.location=classpath:/application.yml")
                .run(context -> {
                    assertThat(context.getEnvironment().getProperty("shopify.agent-auth.environment"))
                            .isEqualTo("test");
                    assertThat(context.getEnvironment().getProperty("shopify.global-catalog.endpoint"))
                            .isEqualTo("https://catalog.shopify.test/api/ucp/mcp");
                });
    }

    @Test
    void bindsProductionDefaultsFromTheSingleApplicationConfigurationBoundary() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues("spring.config.location=classpath:/application.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ShopifyGlobalCatalogProperties properties = context.getBean(
                            ShopifyGlobalCatalogProperties.class
                    );

                    assertThat(properties.endpoint())
                            .isEqualTo(URI.create("https://catalog.shopify.test/api/ucp/mcp"));
                    assertThat(properties.allowedHosts()).isEqualTo(Set.of("catalog.shopify.test"));
                    assertThat(properties.sourceIdentity()).isEqualTo("SHOPIFY_GLOBAL_CATALOG");
                    assertThat(properties.protocolVersion()).isEqualTo("2026-04-08");
                    assertThat(properties.extensionId()).isEqualTo("dev.shopify.catalog.global");
                    assertThat(properties.extensionVersion()).isEqualTo("2026-04-08");
                    assertThat(properties.requiredScopes()).containsExactly("read_global_api_catalog_search");
                    assertThat(properties.maximumResultLimit()).isEqualTo(50);
                    assertThat(properties.requestDeadline()).isEqualTo(Duration.ofSeconds(10));
                });
    }

    @Test
    void bindsSessionOnlyDataUseDefaultAndExplicitBoundedDurations() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues("spring.config.location=classpath:/application.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ShopifyCatalogDataUseProperties properties = context.getBean(
                            ShopifyCatalogDataUseProperties.class
                    );
                    assertThat(properties.searchPersistenceApproved()).isFalse();
                    assertThat(properties.approvedSearchCacheTtl()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(properties.rehydratedFactsTtl()).isEqualTo(Duration.ofMinutes(2));
                });
    }

    @Configuration
    @EnableConfigurationProperties({ShopifyGlobalCatalogProperties.class, ShopifyCatalogDataUseProperties.class})
    static class TestConfiguration {
    }
}
