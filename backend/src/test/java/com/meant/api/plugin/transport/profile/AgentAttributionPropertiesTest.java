package com.meant.api.plugin.transport.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.support.UcpAttribution;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AgentAttributionPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsTheServerControlledMeantAttribution() {
        contextRunner.withPropertyValues(
                        "ucp.agent.attribution.referring-domain=app.usemeant.com",
                        "ucp.agent.attribution.utm-source=meant",
                        "ucp.agent.attribution.utm-medium=agentic_commerce"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AgentAttributionProperties.class).attribution())
                            .isEqualTo(new UcpAttribution(
                                    "app.usemeant.com", "meant", "agentic_commerce"));
                });
    }

    @Test
    void requiresEveryAttributionValueAtStartup() {
        contextRunner.withPropertyValues(
                        "ucp.agent.attribution.referring-domain=app.usemeant.com",
                        "ucp.agent.attribution.utm-source=meant"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(AgentAttributionProperties.class)
    static class TestConfiguration {
    }
}
