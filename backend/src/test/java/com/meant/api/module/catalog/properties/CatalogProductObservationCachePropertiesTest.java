package com.meant.api.module.catalog.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class CatalogProductObservationCachePropertiesTest {

    @Test
    void bindsTheBoundedObservationCacheFromApplicationYaml() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues("spring.config.location=classpath:/application.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CatalogProductObservationCacheProperties properties =
                            context.getBean(CatalogProductObservationCacheProperties.class);

                    assertThat(properties.maximumTtl()).isEqualTo(Duration.ofMinutes(2));
                    assertThat(properties.rehydrationMaximumSize()).isEqualTo(10_000);
                    assertThat(properties.detailMaximumSize()).isEqualTo(2_000);
                });
    }

    @Configuration
    @EnableConfigurationProperties(CatalogProductObservationCacheProperties.class)
    static class TestConfiguration {
    }
}
