package com.meant.api.common.config;

import com.meant.api.common.properties.CorsProperties;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configures the API as a stateless OAuth2 resource server that validates Supabase-issued JWTs.
 *
 * <p>Authentication is performed by Supabase (email/Google/Apple); this service only verifies the
 * bearer token against Supabase's JWKS endpoint (configured via
 * {@code spring.security.oauth2.resourceserver.jwt.*}). Only explicit operational and API
 * documentation endpoints are public; business APIs require authentication by default.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration {

    private static final Set<String> LOCAL_DEVELOPMENT_ORIGINS = Set.of(
            "http://localhost:3000",
            "http://127.0.0.1:3000"
    );

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/info",
            "/.well-known/ucp-agent.json",
            "/api/webhooks/shopify/orders",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final CorsProperties corsProperties;
    private final Environment environment;

    @PostConstruct
    void validateCorsConfiguration() {
        List<String> origins = allowedOrigins();
        if (origins.isEmpty()) {
            throw new IllegalStateException("app.cors.allowed-origins must contain at least one origin");
        }
        if (origins.contains("*")) {
            throw new IllegalStateException("app.cors.allowed-origins must not contain wildcard origins");
        }
        if (isProductionProfile() && origins.stream().anyMatch(LOCAL_DEVELOPMENT_ORIGINS::contains)) {
            throw new IllegalStateException(
                    "Production profile requires APP_CORS_ALLOWED_ORIGINS to be set to explicit non-localhost origins"
            );
        }
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ExpensiveEndpointRateLimitFilter expensiveEndpointRateLimitFilter
    ) {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterAfter(expensiveEndpointRateLimitFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/carts/**").authenticated()
                        .requestMatchers("/api/orders/**").authenticated()
                        .requestMatchers("/api/users/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                }));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private List<String> allowedOrigins() {
        return corsProperties.allowedOrigins().stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .distinct()
                .toList();
    }

    private boolean isProductionProfile() {
        return environment.acceptsProfiles(Profiles.of("prod", "production"));
    }
}
