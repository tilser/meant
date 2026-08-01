package com.meant.api.common.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RateLimitPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsEndpointPathWithoutLeadingSlash() {
        RateLimitProperties properties = properties("api/v1/users/me/product-searches");

        assertThat(validator.validate(properties))
                .anySatisfy(violation -> assertThat(violation.getMessage()).isEqualTo("Path must start with '/'"));
    }

    @Test
    void productBrowsingAndVariantSelectionAreNotRateLimited() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues("spring.config.location=classpath:/application.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RateLimitProperties.class).expensiveEndpoints().endpoints())
                            .noneMatch(endpoint -> endpoint.method().equals("GET")
                                    && endpoint.path().equals(
                                            "/api/v1/users/me/products/{canonicalProductKey}"))
                            .noneMatch(endpoint -> endpoint.method().equals("POST")
                                    && endpoint.path().equals(
                                            "/api/v1/users/me/product-variant-selections"));
                });
    }

    private static RateLimitProperties properties(String path) {
        return new RateLimitProperties(
                true,
                new RateLimitProperties.ExpensiveEndpoints(
                        List.of(new RateLimitProperties.Endpoint("POST", path)),
                        List.of(new RateLimitProperties.Limit(
                                "per-minute",
                                10,
                                10,
                                Duration.ofMinutes(1)
                        )),
                        new RateLimitProperties.BucketCache(1_000L, Duration.ofHours(25))
                )
        );
    }

    @Configuration
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class TestConfiguration {
    }
}
