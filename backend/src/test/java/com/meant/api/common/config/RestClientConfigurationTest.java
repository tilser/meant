package com.meant.api.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.properties.RestClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class RestClientConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "rest-client.connect-timeout-milliseconds=1234",
                    "rest-client.read-timeout-milliseconds=5678"
            );

    @Test
    void restClientBuilderUsesConfiguredTimeouts() {
        contextRunner.run(context -> {
            RestClient restClient = context.getBean(RestClient.Builder.class).build();
            Object requestFactory = ReflectionTestUtils.getField(restClient, "clientRequestFactory");

            assertThat(requestFactory).isInstanceOf(SimpleClientHttpRequestFactory.class);
            assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout")).isEqualTo(1234);
            assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(5678);
        });
    }

    @Configuration
    @Import(RestClientConfiguration.class)
    @EnableConfigurationProperties(RestClientProperties.class)
    static class TestConfiguration {
    }
}
