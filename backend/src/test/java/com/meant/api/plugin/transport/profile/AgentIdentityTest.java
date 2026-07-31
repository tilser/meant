package com.meant.api.plugin.transport.profile;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AgentIdentityTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAbsoluteProfileUrls() {
        for (String profileUrl : List.of(
                "https://usemeant.com/.well-known/ucp-agent.json",
                "https://api.usemeant.com/.well-known/ucp-agent.json",
                "http://localhost:8080/.well-known/ucp-agent.json",
                "https://shopify.dev/ucp/agent-profiles/2026-04-08/valid-with-capabilities.json",
                "https://example.com/.well-known/ucp-agent.json"
        )) {
            assertThat(validator.validate(identity(profileUrl)))
                    .as("profile URL %s", profileUrl)
                    .isEmpty();
        }
    }

    @Test
    void rejectsRelativeProfileUrls() {
        for (String profileUrl : List.of(
                "/.well-known/ucp-agent.json",
                "profiles/ucp-agent.json",
                "ucp-agent.json"
        )) {
            assertThat(validator.validate(identity(profileUrl)))
                    .as("profile URL %s", profileUrl)
                    .anySatisfy(violation -> assertThat(violation.getMessage())
                            .contains("absolute"));
        }
    }

    @Test
    void requiresProfileUrlAtStartup() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "ucp.agent.protocol-version=2026-04-08",
                        "ucp.agent.signing-key-id=test-key"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    private AgentIdentity identity(String profileUrl) {
        return new AgentIdentity(URI.create(profileUrl), "2026-04-08", "test-key");
    }

    @Configuration
    @EnableConfigurationProperties(AgentIdentity.class)
    static class TestConfiguration {
    }
}
