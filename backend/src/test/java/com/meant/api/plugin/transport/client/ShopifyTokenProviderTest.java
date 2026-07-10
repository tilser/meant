package com.meant.api.plugin.transport.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.plugin.transport.dto.ShopifyTokenScopeDecision.Availability;
import com.meant.api.plugin.transport.dto.ShopifyTokenResponse;
import com.meant.api.plugin.transport.profile.ShopifyAgentAuthProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class ShopifyTokenProviderTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final Set<String> CATALOG_SCOPE = Set.of("catalog:read");

    @Test
    void disabledApplicationContextStartsWithoutCredentialsOrTokenAcquisition() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(DisabledAuthConfiguration.class)
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
                    ShopifyBearerAuthenticationResult result = context
                            .getBean(ShopifyBearerAuthenticationStrategy.class)
                            .prepare(CATALOG_SCOPE);
                    assertThat(result.decision().availability()).isEqualTo(Availability.DISABLED);
                    assertThat(result.header()).isEmpty();
                });
    }

    @Test
    void returnsCachedTokenWhileItRemainsBeyondRefreshSkew() {
        TestContext context = context(Duration.ofSeconds(5));
        context.server().expect(requestTo(context.endpoint()))
                .andRespond(tokenResponse("first-token", "catalog:read", 60));

        ShopifyBearerAuthenticationResult first = context.strategy().prepare(CATALOG_SCOPE);
        ShopifyBearerAuthenticationResult second = context.strategy().prepare(CATALOG_SCOPE);

        assertThat(first.decision().available()).isTrue();
        assertThat(second.decision().available()).isTrue();
        context.server().verify();
    }

    @Test
    void refreshesInsideConfiguredSkewAndAfterExpiry() {
        TestContext context = context(Duration.ofSeconds(5));
        context.server().expect(requestTo(context.endpoint()))
                .andRespond(tokenResponse("first-token", "catalog:read", 10));
        context.server().expect(requestTo(context.endpoint()))
                .andRespond(tokenResponse("second-token", "catalog:read", 10));
        context.server().expect(requestTo(context.endpoint()))
                .andRespond(tokenResponse("third-token", "catalog:read", 10));

        context.strategy().prepare(CATALOG_SCOPE);
        context.clock().advance(Duration.ofSeconds(6));
        context.strategy().prepare(CATALOG_SCOPE);
        context.clock().advance(Duration.ofSeconds(11));
        context.strategy().prepare(CATALOG_SCOPE);

        context.server().verify();
    }

    @Test
    void concurrentRequestsShareOneRefresh() throws Exception {
        TestContext context = context(Duration.ofSeconds(5));
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        context.server().expect(requestTo(context.endpoint())).andRespond(request -> {
            handlerEntered.countDown();
            try {
                if (!releaseHandler.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release fake token response");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to release fake token response");
            }
            return withSuccess(tokenBody("shared-token", "catalog:read", 60), MediaType.APPLICATION_JSON)
                    .createResponse(request);
        });

        int callers = 12;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            List<Future<ShopifyBearerAuthenticationResult>> futures = new ArrayList<>();
            for (int index = 0; index < callers; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return context.strategy().prepare(CATALOG_SCOPE);
                }));
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(handlerEntered.await(2, TimeUnit.SECONDS)).isTrue();
            releaseHandler.countDown();
            for (Future<ShopifyBearerAuthenticationResult> future : futures) {
                assertThat(future.get(2, TimeUnit.SECONDS).decision().available()).isTrue();
            }
        }

        context.server().verify();
    }

    @Test
    void clearsFailedSingleFlightWhenTokenAcquisitionThrowsAnError() {
        ShopifyAgentAuthProperties properties = properties(Duration.ofSeconds(5));
        AtomicInteger attempts = new AtomicInteger();
        ShopifyTokenClient client = new ShopifyTokenClient(RestClient.builder(), properties) {
            @Override
            public ShopifyTokenResponse exchangeClientCredentials() {
                if (attempts.getAndIncrement() == 0) {
                    throw new AssertionError("simulated acquisition error");
                }
                return new ShopifyTokenResponse("recovered-token", "Bearer", 60L, "catalog:read");
            }
        };
        ShopifyTokenProvider provider = new ShopifyTokenProvider(
                client,
                properties,
                new ObjectMapper(),
                new MutableClock(NOW)
        );

        assertThatThrownBy(provider::currentToken)
                .isInstanceOf(AssertionError.class)
                .hasMessage("simulated acquisition error");

        assertThat(provider.currentToken().value()).isEqualTo("recovered-token");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void extractsOptionalJwtExpiryScopesAndLimitsWithoutTreatingClaimsAsVerification() {
        TestContext context = context(Duration.ofSeconds(5));
        String token = jwt("""
                {
                  "exp": %d,
                  "scopes": ["catalog:read", "cart:write"],
                  "limits": {
                    "catalog_per_minute": 120,
                    "checkout_per_minute": 20,
                    "temporarily_unavailable": null
                  }
                }
                """.formatted(NOW.plusSeconds(120).getEpochSecond()));
        context.server().expect(requestTo(context.endpoint()))
                .andRespond(withSuccess("{\"access_token\":\"" + token + "\"}", MediaType.APPLICATION_JSON));

        ShopifyBearerAuthenticationResult result = context.strategy().prepare(Set.of("cart:write"));

        assertThat(result.decision().available()).isTrue();
        assertThat(result.decision().metadata()).hasValueSatisfying(metadata -> {
            assertThat(metadata.expiresAt()).isEqualTo(NOW.plusSeconds(120));
            assertThat(metadata.scopes()).containsExactlyInAnyOrder("catalog:read", "cart:write");
            assertThat(metadata.limits().values())
                    .containsEntry("catalog_per_minute", 120L)
                    .doesNotContainKey("temporarily_unavailable");
        });
        context.server().verify();
    }

    @Test
    void usesConfiguredFallbackTtlWhenResponseAndOpaqueTokenLackExpiry() {
        TestContext context = context(Duration.ofSeconds(5));
        context.server().expect(requestTo(context.endpoint()))
                .andRespond(withSuccess("""
                        {
                          "access_token": "opaque-token",
                          "scope": "catalog:read"
                        }
                        """, MediaType.APPLICATION_JSON));

        ShopifyBearerAuthenticationResult result = context.strategy().prepare(CATALOG_SCOPE);

        assertThat(result.decision().metadata())
                .hasValueSatisfying(metadata -> assertThat(metadata.expiresAt()).isEqualTo(NOW.plusSeconds(3600)));
        context.server().verify();
    }

    @Test
    void explicitUnauthorizedPathRefreshesOnce() {
        TestContext context = context(Duration.ofSeconds(5));
        context.server().expect(requestTo(context.endpoint()))
                .andRespond(tokenResponse("rejected-token", "catalog:read", 60));
        context.server().expect(requestTo(context.endpoint()))
                .andRespond(tokenResponse("replacement-token", "catalog:read", 60));

        ShopifyBearerAuthenticationResult rejected = context.strategy().prepare(CATALOG_SCOPE);
        ShopifyBearerAuthenticationResult replacement = context.strategy()
                .refreshAfterUnauthorized(rejected, CATALOG_SCOPE);
        ShopifyBearerAuthenticationResult duplicateRetry = context.strategy()
                .refreshAfterUnauthorized(rejected, CATALOG_SCOPE);

        assertThat(replacement.decision().available()).isTrue();
        HttpHeaders headers = new HttpHeaders();
        replacement.applyTo(headers);
        assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isTrue();
        assertThat(duplicateRetry.decision().availability())
                .isEqualTo(Availability.UNAUTHORIZED_REFRESH_ALREADY_ATTEMPTED);
        context.server().verify();
    }

    @Test
    void missingScopeReturnsTypedUnavailableDecisionWithoutBearerHeader() {
        TestContext context = context(Duration.ofSeconds(5));
        context.server().expect(requestTo(context.endpoint()))
                .andRespond(tokenResponse("catalog-only-token", "catalog:read", 60));

        ShopifyBearerAuthenticationResult result = context.strategy().prepare(Set.of("orders:read"));

        assertThat(result.decision().availability()).isEqualTo(Availability.MISSING_SCOPES);
        assertThat(result.decision().missingScopes()).containsExactly("orders:read");
        assertThat(result.header()).isEmpty();
        context.server().verify();
    }

    @Test
    void bearerContainersRedactTokenValues() {
        TestContext context = context(Duration.ofSeconds(5));
        context.server().expect(requestTo(context.endpoint()))
                .andRespond(tokenResponse("private-token-value", "catalog:read", 60));

        ShopifyBearerAuthenticationResult result = context.strategy().prepare(CATALOG_SCOPE);

        assertThat(result.toString()).contains("[redacted]").doesNotContain("private-token-value");
        assertThat(result.header().orElseThrow().toString())
                .contains("[redacted]")
                .doesNotContain("private-token-value");
        context.server().verify();
    }

    private TestContext context(Duration refreshSkew) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        URI endpoint = URI.create("https://api.shopify.test/auth/access_token");
        ShopifyAgentAuthProperties properties = properties(refreshSkew);
        MutableClock clock = new MutableClock(NOW);
        ShopifyTokenClient client = new ShopifyTokenClient(builder, properties);
        ShopifyTokenProvider provider = new ShopifyTokenProvider(client, properties, new ObjectMapper(), clock);
        return new TestContext(
                endpoint.toString(),
                server,
                clock,
                new ShopifyBearerAuthenticationStrategy(provider, properties)
        );
    }

    private ShopifyAgentAuthProperties properties(Duration refreshSkew) {
        return new ShopifyAgentAuthProperties(
                true,
                "test",
                "configured-client",
                "configured-secret",
                URI.create("https://api.shopify.test/auth/access_token"),
                refreshSkew,
                Duration.ofHours(1)
        );
    }

    private static org.springframework.test.web.client.ResponseCreator tokenResponse(
            String token,
            String scope,
            long expiresIn
    ) {
        return withSuccess(tokenBody(token, scope, expiresIn), MediaType.APPLICATION_JSON);
    }

    private static String tokenBody(String token, String scope, long expiresIn) {
        return """
                {
                  "access_token": "%s",
                  "token_type": "Bearer",
                  "scope": "%s",
                  "expires_in": %d
                }
                """.formatted(token, scope, expiresIn);
    }

    private static String jwt(String payload) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
                + "."
                + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + ".signature";
    }

    private record TestContext(
            String endpoint,
            MockRestServiceServer server,
            MutableClock clock,
            ShopifyBearerAuthenticationStrategy strategy
    ) {
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Configuration
    @EnableConfigurationProperties(ShopifyAgentAuthProperties.class)
    @Import({
            ShopifyTokenClient.class,
            ShopifyTokenProvider.class,
            ShopifyBearerAuthenticationStrategy.class
    })
    static class DisabledAuthConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
