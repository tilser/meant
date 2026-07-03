package com.meant.api.module.discount.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DiscountCodeSearchPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "discount.code-search.candidate-cache-ttl=48h",
                    "discount.code-search.invalid-cache-ttl=12h",
                    "discount.code-search.failed-cache-ttl=1h",
                    "discount.code-search.max-candidates=10",
                    "discount.code-search.openrouter-model=openai/gpt-5.2:online",
                    "discount.code-search.web-max-results=8"
            );

    @Test
    void bindsConfiguredValues() {
        contextRunner.run(context -> {
            DiscountCodeSearchProperties properties = context.getBean(DiscountCodeSearchProperties.class);

            assertThat(properties.candidateCacheTtl()).isEqualTo(Duration.ofHours(48));
            assertThat(properties.invalidCacheTtl()).isEqualTo(Duration.ofHours(12));
            assertThat(properties.failedCacheTtl()).isEqualTo(Duration.ofHours(1));
            assertThat(properties.maxCandidates()).isEqualTo(10);
            assertThat(properties.openRouterModel()).isEqualTo("openai/gpt-5.2:online");
            assertThat(properties.webMaxResults()).isEqualTo(8);
        });
    }

    @Test
    void childValuesStillBindWhenParentKeyIsPresent() {
        contextRunner.withPropertyValues("discount.code-search=")
                .run(context -> {
                    DiscountCodeSearchProperties properties = context.getBean(DiscountCodeSearchProperties.class);

                    assertThat(properties.candidateCacheTtl()).isEqualTo(Duration.ofHours(48));
                    assertThat(properties.maxCandidates()).isEqualTo(10);
                });
    }

    @Test
    void bindsValuesFromApplicationYaml() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues("spring.config.location=classpath:/application.yml")
                .run(context -> {
                    DiscountCodeSearchProperties properties = context.getBean(DiscountCodeSearchProperties.class);

                    assertThat(properties.candidateCacheTtl()).isEqualTo(Duration.ofHours(48));
                    assertThat(properties.invalidCacheTtl()).isEqualTo(Duration.ofHours(12));
                    assertThat(properties.failedCacheTtl()).isEqualTo(Duration.ofHours(1));
                    assertThat(properties.maxCandidates()).isEqualTo(10);
                    assertThat(properties.openRouterModel()).isEqualTo("openai/gpt-5.2:online");
                    assertThat(properties.webMaxResults()).isEqualTo(8);
                });
    }

    @Configuration
    @EnableConfigurationProperties(DiscountCodeSearchProperties.class)
    static class TestConfiguration {
    }
}
