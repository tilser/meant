package com.meant.api.plugin.transport.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogArguments;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogArguments.Catalog;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyGlobalCatalogArguments.Pagination;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.dto.ShopifyTokenResponse;
import com.meant.api.plugin.transport.dto.ShopifyUcpRequestOptions;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import com.meant.api.plugin.transport.profile.ShopifyAgentAuthProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class ShopifyAuthenticatedUcpClientTest {

    private static final String SCOPE = "read_global_api_catalog_search";
    private static final URI ENDPOINT = URI.create("https://catalog.shopify.test/api/ucp/mcp");
    private static final URI PROFILE = URI.create("https://meant.test/.well-known/ucp");

    @Test
    void sendsTypedToolRequestWithAgentProfileBearerAndConfiguredLimit() {
        TestClient context = client(tokenClient("catalog-token", SCOPE));
        context.server().expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer catalog-token"))
                .andExpect(jsonPath("$.method").value("tools/call"))
                .andExpect(jsonPath("$.params.name").value("search_catalog"))
                .andExpect(jsonPath("$.params.arguments.meta.ucp-agent.profile").value(PROFILE.toString()))
                .andExpect(jsonPath("$.params.arguments.catalog.query").value("trail shoes"))
                .andExpect(jsonPath("$.params.arguments.catalog.pagination.limit").value(17))
                .andRespond(withSuccess(successEnvelope(), MediaType.APPLICATION_JSON));

        try {
            UcpToolResponse response = context.client().callTool(
                    options(Duration.ofSeconds(1)),
                    "search_catalog",
                    searchArguments(17)
            );

            assertThat(response.negotiatedCapabilities().version(
                    com.meant.api.plugin.catalog.search.CatalogSearchCapability.ID
            )).contains("2026-04-08");
            context.server().verify();
        } finally {
            context.client().close();
        }
    }

    @Test
    void refreshesAndRetriesExactlyOnceAfterUnauthorized() {
        AtomicInteger tokens = new AtomicInteger();
        TestClient context = client(tokenClient(() -> tokens.getAndIncrement() == 0
                ? new ShopifyTokenResponse("rejected-token", "Bearer", 3600L, SCOPE)
                : new ShopifyTokenResponse("replacement-token", "Bearer", 3600L, SCOPE)));
        context.server().expect(requestTo(ENDPOINT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer rejected-token"))
                .andRespond(withRawStatus(401));
        context.server().expect(requestTo(ENDPOINT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer replacement-token"))
                .andRespond(withSuccess(successEnvelope(), MediaType.APPLICATION_JSON));

        try {
            context.client().callTool(options(Duration.ofSeconds(1)), "search_catalog", searchArguments(10));

            assertThat(tokens).hasValue(2);
            context.server().verify();
        } finally {
            context.client().close();
        }
    }

    @Test
    void doesNotRetrySecondUnauthorizedAndRedactsResponseSecrets() {
        AtomicInteger tokens = new AtomicInteger();
        TestClient context = client(tokenClient(() -> new ShopifyTokenResponse(
                "private-token-" + tokens.incrementAndGet(), "Bearer", 3600L, SCOPE)));
        context.server().expect(requestTo(ENDPOINT)).andRespond(withRawStatus(401)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"authorization\":\"Bearer reflected-secret\",\"access_token\":\"reflected-token\"}"));
        context.server().expect(requestTo(ENDPOINT)).andRespond(withRawStatus(401)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"client_secret\":\"reflected-client-secret\"}"));

        try {
            assertThatThrownBy(() -> context.client().callTool(
                    options(Duration.ofSeconds(1)), "search_catalog", searchArguments(10)))
                    .isInstanceOfSatisfying(ShopifyUcpTransportException.class, exception -> {
                        assertThat(exception.failure()).isEqualTo(ShopifyUcpTransportFailure.AUTHENTICATION);
                        assertThat(exception.toString())
                                .doesNotContain("private-token")
                                .doesNotContain("reflected-secret")
                                .doesNotContain("reflected-token")
                                .doesNotContain("reflected-client-secret");
                        assertThat(exception.getCause()).isNotNull()
                                .extracting(Throwable::toString)
                                .asString()
                                .doesNotContain("reflected");
                    });
            assertThat(tokens).hasValue(2);
            context.server().verify();
        } finally {
            context.client().close();
        }
    }

    @Test
    void classifiesMissingScopeWithoutMakingCatalogRequest() {
        TestClient context = client(tokenClient("wrong-scope-token", "other_scope"));

        try {
            assertThatThrownBy(() -> context.client().callTool(
                    options(Duration.ofSeconds(1)), "search_catalog", searchArguments(10)))
                    .isInstanceOfSatisfying(ShopifyUcpTransportException.class, exception ->
                            assertThat(exception.failure()).isEqualTo(ShopifyUcpTransportFailure.AUTHENTICATION));
            context.server().verify();
        } finally {
            context.client().close();
        }
    }

    @Test
    void classifiesForbiddenAsAuthorizationFailureWithoutRefreshRetry() {
        AtomicInteger tokens = new AtomicInteger();
        TestClient context = client(tokenClient(() -> {
            tokens.incrementAndGet();
            return new ShopifyTokenResponse("permission-token", "Bearer", 3600L, SCOPE);
        }));
        context.server().expect(requestTo(ENDPOINT)).andRespond(withRawStatus(403));

        try {
            assertThatThrownBy(() -> context.client().callTool(
                    options(Duration.ofSeconds(1)), "search_catalog", searchArguments(10)))
                    .isInstanceOfSatisfying(ShopifyUcpTransportException.class, exception -> {
                        assertThat(exception.failure()).isEqualTo(ShopifyUcpTransportFailure.AUTHENTICATION);
                        assertThat(exception.upstreamStatus()).contains(403);
                    });
            assertThat(tokens).hasValue(1);
            context.server().verify();
        } finally {
            context.client().close();
        }
    }

    @Test
    void exposesRateLimitRetryMetadataWithoutResponseBody() {
        TestClient context = client(tokenClient("catalog-token", SCOPE));
        context.server().expect(requestTo(ENDPOINT)).andRespond(withRawStatus(429)
                .header(HttpHeaders.RETRY_AFTER, "9")
                .body("Bearer reflected-rate-limit-token"));

        try {
            assertThatThrownBy(() -> context.client().callTool(
                    options(Duration.ofSeconds(1)), "search_catalog", searchArguments(10)))
                    .isInstanceOfSatisfying(ShopifyUcpTransportException.class, exception -> {
                        assertThat(exception.failure()).isEqualTo(ShopifyUcpTransportFailure.RATE_LIMITED);
                        assertThat(exception.retryAfter()).contains(Duration.ofSeconds(9));
                        assertThat(exception.upstreamStatus()).contains(429);
                        assertThat(exception.toString()).doesNotContain("reflected-rate-limit-token");
                    });
        } finally {
            context.client().close();
        }
    }

    @Test
    void enforcesOverallRequestDeadline() {
        TestClient context = client(tokenClient("catalog-token", SCOPE));
        context.server().expect(requestTo(ENDPOINT)).andRespond(request -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return withSuccess(successEnvelope(), MediaType.APPLICATION_JSON).createResponse(request);
        });

        try {
            assertThatThrownBy(() -> context.client().callTool(
                    options(Duration.ofMillis(20)), "search_catalog", searchArguments(10)))
                    .isInstanceOfSatisfying(ShopifyUcpTransportException.class, exception ->
                            assertThat(exception.failure()).isEqualTo(ShopifyUcpTransportFailure.TIMEOUT));
        } finally {
            context.client().close();
        }
    }

    @Test
    void classifiesTransientAndMalformedResponses() {
        TestClient transientContext = client(tokenClient("catalog-token", SCOPE));
        transientContext.server().expect(requestTo(ENDPOINT)).andRespond(withRawStatus(503));
        try {
            assertThatThrownBy(() -> transientContext.client().callTool(
                    options(Duration.ofSeconds(1)), "search_catalog", searchArguments(10)))
                    .isInstanceOfSatisfying(ShopifyUcpTransportException.class, exception ->
                            assertThat(exception.failure()).isEqualTo(ShopifyUcpTransportFailure.TRANSIENT_UPSTREAM));
        } finally {
            transientContext.client().close();
        }

        TestClient malformedContext = client(tokenClient("catalog-token", SCOPE));
        malformedContext.server().expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        try {
            assertThatThrownBy(() -> malformedContext.client().callTool(
                    options(Duration.ofSeconds(1)), "search_catalog", searchArguments(10)))
                    .isInstanceOfSatisfying(ShopifyUcpTransportException.class, exception ->
                            assertThat(exception.failure()).isEqualTo(ShopifyUcpTransportFailure.MALFORMED_RESPONSE));
        } finally {
            malformedContext.client().close();
        }
    }

    @Test
    void rejectsUnallowlistedOrInsecureEndpointsBeforeAuthenticationCanRun() {
        assertThatThrownBy(() -> new ShopifyUcpRequestOptions(
                URI.create("https://generic-ucp.test/api/ucp/mcp"),
                Set.of("catalog.shopify.test"),
                Set.of(SCOPE),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowlisted");

        assertThatThrownBy(() -> new ShopifyUcpRequestOptions(
                URI.create("http://catalog.shopify.test/api/ucp/mcp"),
                Set.of("catalog.shopify.test"),
                Set.of(SCOPE),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void genericUcpTransportNeverReceivesShopifyCredentialsThroughGlobalInterception() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        URI genericEndpoint = URI.create("https://generic-ucp.test/api/ucp/mcp");
        server.expect(requestTo(genericEndpoint))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(successEnvelope(), MediaType.APPLICATION_JSON));
        UcpMcpClient genericClient = new UcpMcpClient(new AgentIdentity(PROFILE, "2026-04-08", "test-key"));

        genericClient.callTool(builder.build(), genericEndpoint, "search_catalog", searchArguments(10));

        server.verify();
    }

    private TestClient client(ShopifyTokenClient tokenClient) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ShopifyAgentAuthProperties authProperties = authProperties();
        ShopifyTokenProvider tokenProvider = new ShopifyTokenProvider(
                tokenClient,
                authProperties,
                new ObjectMapper(),
                Clock.systemUTC()
        );
        ShopifyBearerAuthenticationStrategy authentication = new ShopifyBearerAuthenticationStrategy(
                tokenProvider,
                authProperties
        );
        UcpMcpClient mcpClient = new UcpMcpClient(new AgentIdentity(PROFILE, "2026-04-08", "test-key"));
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ShopifyAuthenticatedUcpClient client = new ShopifyAuthenticatedUcpClient(
                builder.build(),
                mcpClient,
                authentication,
                executor,
                Clock.systemUTC()
        );
        return new TestClient(client, server);
    }

    private ShopifyTokenClient tokenClient(String token, String scope) {
        return tokenClient(() -> new ShopifyTokenResponse(token, "Bearer", 3600L, scope));
    }

    private ShopifyTokenClient tokenClient(java.util.function.Supplier<ShopifyTokenResponse> responses) {
        return new ShopifyTokenClient(RestClient.builder(), authProperties()) {
            @Override
            public ShopifyTokenResponse exchangeClientCredentials() {
                return responses.get();
            }
        };
    }

    private ShopifyAgentAuthProperties authProperties() {
        return new ShopifyAgentAuthProperties(
                true,
                "test",
                "client",
                "secret",
                URI.create("https://api.shopify.test/auth/access_token"),
                Duration.ofMinutes(5),
                Duration.ofHours(1)
        );
    }

    private ShopifyUcpRequestOptions options(Duration deadline) {
        return new ShopifyUcpRequestOptions(
                ENDPOINT,
                Set.of("catalog.shopify.test"),
                Set.of(SCOPE),
                Duration.ofMillis(100),
                Duration.ofSeconds(1),
                deadline
        );
    }

    private ShopifyGlobalCatalogArguments searchArguments(int limit) {
        return new ShopifyGlobalCatalogArguments(new Catalog(
                "trail shoes", null, null, null, null, null, null, "offer", new Pagination(null, limit)
        ));
    }

    private String successEnvelope() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "isError": false,
                    "structuredContent": {
                      "ucp": {
                        "version": "2026-04-08",
                        "capabilities": {
                          "dev.ucp.shopping.catalog.search": [{"version": "2026-04-08"}],
                          "dev.shopify.catalog.global": [{"version": "2026-04-08"}]
                        }
                      },
                      "products": []
                    }
                  }
                }
                """;
    }

    private record TestClient(ShopifyAuthenticatedUcpClient client, MockRestServiceServer server) {
    }
}
