package com.meant.api.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.properties.RateLimitProperties;
import com.meant.api.common.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ExpensiveEndpointRateLimitFilterTest {

    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void limitsExpensiveAuthenticatedRequestsByUserSubject() throws Exception {
        ExpensiveEndpointRateLimitFilter filter = filter(
                2,
                endpoint("POST", "/api/v1/users/me/product-searches")
        );
        authenticate("user-1");

        assertAllowed(filter, request("POST", "/api/v1/users/me/product-searches", "203.0.113.10"));
        assertAllowed(filter, request("POST", "/api/v1/users/me/product-searches", "203.0.113.11"));

        MockHttpServletResponse response = doFilter(
                filter,
                request("POST", "/api/v1/users/me/product-searches", "203.0.113.12")
        );

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat(response.getHeader("X-Rate-Limit-Retry-After-Seconds")).isEqualTo("30");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString()).contains("rate_limit_exceeded");
    }

    @Test
    void limitsAnonymousExpensiveRequestsByClientIp() throws Exception {
        ExpensiveEndpointRateLimitFilter filter = filter(
                1,
                endpoint("POST", "/api/merchants/semantic-product-search")
        );

        assertAllowed(filter, request("POST", "/api/merchants/semantic-product-search", "203.0.113.20"));

        MockHttpServletResponse limited = doFilter(
                filter,
                request("POST", "/api/merchants/semantic-product-search", "203.0.113.20")
        );
        assertThat(limited.getStatus()).isEqualTo(429);

        assertAllowed(filter, request("POST", "/api/merchants/semantic-product-search", "203.0.113.21"));
    }

    @Test
    void ignoresSpoofableForwardedForHeaderForAnonymousRateLimitKeys() throws Exception {
        ExpensiveEndpointRateLimitFilter filter = filter(
                1,
                endpoint("POST", "/api/merchants/semantic-product-search")
        );

        assertAllowed(filter, request(
                "POST",
                "/api/merchants/semantic-product-search",
                "203.0.113.20",
                "198.51.100.1"
        ));

        MockHttpServletResponse limited = doFilter(
                filter,
                request(
                        "POST",
                        "/api/merchants/semantic-product-search",
                        "203.0.113.20",
                        "198.51.100.2"
                )
        );

        assertThat(limited.getStatus()).isEqualTo(429);
    }

    @Test
    void matchesMatrixParameterVariantsOfConfiguredExpensivePaths() throws Exception {
        ExpensiveEndpointRateLimitFilter filter = filter(
                1,
                endpoint("POST", "/api/v1/users/me/product-searches")
        );
        authenticate("user-1");

        assertAllowed(filter, request("POST", "/api/v1/users/me/product-searches;v=1", "203.0.113.10"));

        MockHttpServletResponse limited = doFilter(
                filter,
                request("POST", "/api/v1/users/me/product-searches;v=2", "203.0.113.10")
        );

        assertThat(limited.getStatus()).isEqualTo(429);
    }

    @Test
    void allowsUnmatchedTrafficWithoutConsumingRateLimit() throws Exception {
        ExpensiveEndpointRateLimitFilter filter = filter(
                1,
                endpoint("POST", "/api/v1/users/me/product-searches")
        );
        authenticate("user-1");

        assertAllowed(filter, request("GET", "/actuator/health", "203.0.113.10"));
        assertAllowed(filter, request("GET", "/actuator/health", "203.0.113.10"));
        assertAllowed(filter, request("POST", "/api/v1/users/me/product-searches", "203.0.113.10"));
    }

    private static ExpensiveEndpointRateLimitFilter filter(
            int perMinuteCapacity,
            RateLimitProperties.Endpoint endpoint
    ) {
        RateLimitProperties properties = new RateLimitProperties(
                true,
                new RateLimitProperties.ExpensiveEndpoints(
                        List.of(endpoint),
                        List.of(
                                new RateLimitProperties.Limit(
                                        "per-minute",
                                        perMinuteCapacity,
                                        perMinuteCapacity,
                                        Duration.ofMinutes(1)
                                ),
                                new RateLimitProperties.Limit(
                                        "per-day",
                                        100,
                                        100,
                                        Duration.ofDays(1)
                                )
                        ),
                        new RateLimitProperties.BucketCache(1_000L, Duration.ofHours(25))
                )
        );
        return new ExpensiveEndpointRateLimitFilter(
                properties,
                new RateLimitService(
                        properties.expensiveEndpoints().limits(),
                        properties.expensiveEndpoints().bucketCache(),
                        Clock.fixed(NOW, ZoneId.of("UTC"))
                )
        );
    }

    private static RateLimitProperties.Endpoint endpoint(String method, String path) {
        return new RateLimitProperties.Endpoint(method, path);
    }

    private static void authenticate(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(NOW)
                .expiresAt(NOW.plus(Duration.ofMinutes(10)))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
    }

    private static MockHttpServletRequest request(String method, String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static MockHttpServletRequest request(
            String method,
            String path,
            String remoteAddr,
            String forwardedFor
    ) {
        MockHttpServletRequest request = request(method, path, remoteAddr);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    private static void assertAllowed(
            ExpensiveEndpointRateLimitFilter filter,
            MockHttpServletRequest request
    ) throws Exception {
        MockHttpServletResponse response = doFilter(filter, request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse doFilter(
            ExpensiveEndpointRateLimitFilter filter,
            MockHttpServletRequest request
    ) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new OkFilterChain());
        return response;
    }

    private static class OkFilterChain implements FilterChain {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException {
            ((MockHttpServletResponse) response).setStatus(200);
        }
    }
}
