package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
                    assertThat(context.getEnvironment().getProperty(
                            "shopify.global-catalog.runtime-discovery-enabled"))
                            .isEqualTo("true");
                    assertThat(context.getEnvironment().getProperty(
                            "commerce.catalog.federation.overall-deadline"))
                            .isEqualTo("51s");
                    assertThat(context.getEnvironment().getProperty(
                            "user.product-search.stream-timeout"))
                            .isEqualTo("60s");
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
                    assertThat(properties.runtimeDiscoveryEnabled()).isTrue();
                    assertThat(properties.discoveryCacheTtl()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(properties.requiredScopes()).containsExactly("read_global_api_catalog_search");
                    assertThat(properties.maximumResultLimit()).isEqualTo(50);
                    assertThat(properties.maximumSearchPages()).isEqualTo(4);
                    assertThat(properties.requestDeadline()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(properties.discoverySourceTimeout()).isEqualTo(Duration.ofSeconds(51));
                });
    }

    @Test
    void acceptsTheBoundedTwoWindowCandidateCapacityWithoutOverflowingPageOrTimeoutArithmetic() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "spring.config.location=classpath:/application.yml",
                        "shopify.global-catalog.default-result-limit=1",
                        "shopify.global-catalog.maximum-result-limit=1",
                        "shopify.global-catalog.maximum-candidates="
                                + ShopifyGlobalCatalogProperties.MAXIMUM_CONFIGURED_CANDIDATES,
                        "shopify.global-catalog.request-deadline=1s"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ShopifyGlobalCatalogProperties properties =
                            context.getBean(ShopifyGlobalCatalogProperties.class);
                    assertThat(properties.maximumSearchPages()).isEqualTo(200);
                    assertThat(properties.discoverySourceTimeout()).isEqualTo(Duration.ofSeconds(202));
                    assertThatCode(() -> properties.discoverySourceTimeout().toNanos())
                            .doesNotThrowAnyException();
                });
    }

    @Test
    void rejectsCandidateCapacityBeyondTheNormalizationAndRankingWindowAtStartup() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "spring.config.location=classpath:/application.yml",
                        "shopify.global-catalog.maximum-candidates="
                                + (ShopifyGlobalCatalogProperties.MAXIMUM_CONFIGURED_CANDIDATES + 1)
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsARequestDeadlineWhoseDerivedSourceTimeoutCannotFitNanoseconds() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "spring.config.location=classpath:/application.yml",
                        "shopify.global-catalog.request-deadline=9223372036854775807ns"
                )
                .run(context -> assertThat(context).hasFailed());
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

    @Test
    void rejectsZeroOrNegativeDataUseDurationsAtStartup() {
        for (String property : Set.of(
                "shopify.global-catalog.data-use.approved-search-cache-ttl",
                "shopify.global-catalog.data-use.rehydrated-facts-ttl"
        )) {
            for (String value : Set.of("0s", "-1s")) {
                new ApplicationContextRunner()
                        .withInitializer(new ConfigDataApplicationContextInitializer())
                        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                        .withUserConfiguration(TestConfiguration.class)
                        .withPropertyValues(
                                "spring.config.location=classpath:/application.yml",
                                property + "=" + value
                        )
                        .run(context -> assertThat(context).hasFailed());
            }
        }
    }

    @Configuration
    @EnableConfigurationProperties({ShopifyGlobalCatalogProperties.class, ShopifyCatalogDataUseProperties.class})
    static class TestConfiguration {
    }
}
