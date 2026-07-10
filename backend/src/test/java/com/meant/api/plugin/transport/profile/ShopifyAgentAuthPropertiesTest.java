package com.meant.api.plugin.transport.profile;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ShopifyAgentAuthPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void disabledConfigurationAllowsMissingCredentials() {
        ShopifyAgentAuthProperties properties = properties(false, "", "", Duration.ofMinutes(5));

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void enabledConfigurationRequiresCredentials() {
        ShopifyAgentAuthProperties properties = properties(true, "", "", Duration.ofMinutes(5));

        assertThat(validator.validate(properties))
                .anySatisfy(violation -> assertThat(violation.getMessage())
                        .isEqualTo("clientId and clientSecret are required when Shopify agent authentication is enabled"));
    }

    @Test
    void rejectsNonPositiveOrUnsafeTokenTiming() {
        ShopifyAgentAuthProperties properties = properties(
                true,
                "configured-client",
                "configured-secret",
                Duration.ofHours(1)
        );

        assertThat(validator.validate(properties))
                .anySatisfy(violation -> assertThat(violation.getMessage())
                        .contains("fallbackTokenTtl must exceed refreshSkew"));
    }

    @Test
    void enabledConfigurationRequiresHttpsTokenEndpoint() {
        ShopifyAgentAuthProperties properties = new ShopifyAgentAuthProperties(
                true,
                "test",
                "configured-client",
                "configured-secret",
                URI.create("http://api.shopify.test/auth/access_token"),
                Duration.ofMinutes(5),
                Duration.ofHours(1)
        );

        assertThat(validator.validate(properties))
                .anySatisfy(violation -> assertThat(violation.getMessage()).contains("use HTTPS"));
    }

    @Test
    void redactsClientSecretFromToString() {
        ShopifyAgentAuthProperties properties = properties(
                true,
                "configured-client",
                "private-client-secret",
                Duration.ofMinutes(5)
        );

        assertThat(properties.toString()).contains("[redacted]").doesNotContain("private-client-secret");
    }

    @Test
    void disabledBoundConfigurationStartsWithoutSecrets() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "shopify.agent-auth.enabled=false",
                        "shopify.agent-auth.environment=test",
                        "shopify.agent-auth.client-id=",
                        "shopify.agent-auth.client-secret=",
                        "shopify.agent-auth.token-endpoint=https://api.shopify.test/auth/access_token",
                        "shopify.agent-auth.refresh-skew=5m",
                        "shopify.agent-auth.fallback-token-ttl=60m"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ShopifyAgentAuthProperties.class).isEnabled()).isFalse();
                });
    }

    private ShopifyAgentAuthProperties properties(
            boolean enabled,
            String clientId,
            String clientSecret,
            Duration refreshSkew
    ) {
        return new ShopifyAgentAuthProperties(
                enabled,
                "test",
                clientId,
                clientSecret,
                URI.create("https://api.shopify.test/auth/access_token"),
                refreshSkew,
                Duration.ofHours(1)
        );
    }

    @Configuration
    @EnableConfigurationProperties(ShopifyAgentAuthProperties.class)
    static class TestConfiguration {
    }
}
