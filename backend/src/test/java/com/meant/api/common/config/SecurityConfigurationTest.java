package com.meant.api.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.common.properties.CorsProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigurationTest {

    @Test
    void corsConfigurationOnlyAppliesToApiPaths() {
        CorsConfigurationSource source = configuration(
                List.of("https://app.example.com"),
                environment()
        ).corsConfigurationSource();

        CorsConfiguration apiConfiguration = source.getCorsConfiguration(request("/api/users/me"));

        assertThat(apiConfiguration).isNotNull();
        assertThat(apiConfiguration.getAllowedOrigins()).containsExactly("https://app.example.com");
        assertThat(apiConfiguration.getAllowedMethods())
                .containsExactly("GET", "POST", "PATCH", "DELETE", "OPTIONS");
        assertThat(source.getCorsConfiguration(request("/actuator/health"))).isNull();
    }

    @Test
    void productionProfileRejectsLocalhostOrigins() {
        SecurityConfiguration configuration = configuration(
                List.of("http://localhost:3000", "http://127.0.0.1:3000"),
                environment("prod")
        );

        assertThatThrownBy(configuration::validateCorsConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS");
    }

    @Test
    void productionProfileAcceptsExplicitOrigins() {
        SecurityConfiguration configuration = configuration(
                List.of("https://app.example.com"),
                environment("production")
        );

        configuration.validateCorsConfiguration();
    }

    @Test
    void wildcardOriginsAreRejected() {
        SecurityConfiguration configuration = configuration(List.of("*"), environment());

        assertThatThrownBy(configuration::validateCorsConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not contain wildcard origins");
    }

    private static SecurityConfiguration configuration(List<String> allowedOrigins, Environment environment) {
        return new SecurityConfiguration(new CorsProperties(allowedOrigins), environment);
    }

    private static Environment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private static MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("OPTIONS", path);
    }
}
