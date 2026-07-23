package com.meant.api.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.port.AgentModelGateway;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class AgentModelConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("commerce.agent.enabled=true");

    @Test
    void defersOpenAiClientConstructionUntilTheModelIsUsed() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AgentModelGateway.class);
            assertThat(context.getBeanFactory().getBeanDefinition("agentChatModel").isLazyInit()).isTrue();
            assertThat(context.getBeanFactory().containsSingleton("agentChatModel")).isFalse();

            context.getBean(AgentModelGateway.class);

            assertThat(context.getBeanFactory().containsSingleton("agentChatModel")).isFalse();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AgentModelConfiguration.class)
    static class TestConfiguration {

        @Bean
        AgentProperties agentProperties() {
            return new AgentProperties(
                    true,
                    "test-model",
                    "test-fallback-model",
                    "https://openrouter.example/api/v1",
                    "",
                    "Meant Test",
                    "https://test.meant.example",
                    "test-prompt",
                    "test-tools",
                    0.0,
                    1024,
                    4,
                    8,
                    2,
                    2,
                    20,
                    16_000,
                    4_000,
                    2,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(20),
                    Duration.ofSeconds(10),
                    Duration.ofMinutes(1),
                    Duration.ofMillis(100),
                    32,
                    Duration.ofDays(7),
                    Duration.ofMinutes(3)
            );
        }

        @Bean
        ObservationRegistry observationRegistry() {
            return ObservationRegistry.create();
        }
    }
}
