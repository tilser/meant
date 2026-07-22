package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.exception.MerchantMcpToolException;
import com.meant.api.module.merchant.properties.MerchantMcpToolProperties;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.plugin.transport.client.UcpMcpClient;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class MerchantMcpToolClientTest {

    @Test
    void usesAllowedAbsoluteEndpoint() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantMcpToolClient client = client(restClientBuilder.build(), "93.184.216.34");
        server.expect(requestTo("https://advertised.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 4,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"ok\\":true}"
                              }
                            ],
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        MerchantMcpToolCallResult result = client.callTool(
                new MerchantSemanticSearchResult(
                        UUID.randomUUID(),
                        "advertised.example",
                        "Merchant",
                        "https://advertised.example/api/mcp",
                        null,
                        "Context",
                        0.9d,
                        0.8d,
                        1
                ),
                "search_catalog",
                Map.of("catalog", Map.of("query", "candle"))
        );

        assertThat(result.endpoint()).isEqualTo("https://advertised.example/api/mcp");
        assertThat(result.contentText()).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void usesDelegatedAdvertisedEndpointOutsideMerchantDomain() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantMcpToolClient client = client(restClientBuilder.build(), "93.184.216.34");
        server.expect(requestTo("https://americangiant.myshopify.com/api/ucp/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 4,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"ok\\":true}"
                              }
                            ],
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        MerchantMcpToolCallResult result = client.callTool(
                new MerchantSemanticSearchResult(
                        UUID.randomUUID(),
                        "american-giant.com",
                        "American Giant",
                        "https://americangiant.myshopify.com/api/ucp/mcp",
                        null,
                        "Context",
                        0.9d,
                        0.8d,
                        1
                ),
                "lookup_catalog",
                Map.of("id", "gid://shopify/Product/2111643746401")
        );

        assertThat(result.endpoint()).isEqualTo("https://americangiant.myshopify.com/api/ucp/mcp");
        assertThat(result.contentText()).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void rejectsRelativeCatalogEndpointWithoutSynthesizingARoute() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantMcpToolClient client = client(restClientBuilder.build(), "93.184.216.34");

        assertThatThrownBy(() -> client.callTool(
                new MerchantSemanticSearchResult(
                        UUID.randomUUID(),
                        "store.example",
                        "Store",
                        "/api/ucp/mcp",
                        null,
                        "Context",
                        0.9d,
                        0.8d,
                        1
                ),
                "search_catalog",
                Map.of("catalog", Map.of("query", "candle"))
        ))
                .isInstanceOf(MerchantMcpToolException.class)
                .hasMessageContaining("Exact MCP tool search_catalog failed");

        server.verify();
    }

    @Test
    void blocksLocalhostEndpointWithoutSendingRequest() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantMcpToolClient client = client(restClientBuilder.build(), "127.0.0.1");

        assertThatThrownBy(() -> client.callTool(
                new MerchantSemanticSearchResult(
                        UUID.randomUUID(),
                        "localhost",
                        "Merchant",
                        "https://localhost/api/mcp",
                        null,
                        "Context",
                        0.9d,
                        0.8d,
                        1
                ),
                "search_catalog",
                Map.of("catalog", Map.of("query", "candle"))
        ))
                .isInstanceOf(MerchantMcpToolException.class)
                .hasMessageContaining("Exact MCP tool search_catalog failed");
        server.verify();
    }

    @Test
    void customTimeoutsDoNotMutateSharedBuilder() {
        SimpleClientHttpRequestFactory sharedRequestFactory = new SimpleClientHttpRequestFactory();
        sharedRequestFactory.setConnectTimeout(Duration.ofMillis(1234));
        sharedRequestFactory.setReadTimeout(Duration.ofMillis(5678));
        RestClient.Builder restClientBuilder = RestClient.builder().requestFactory(sharedRequestFactory);

        MerchantMcpToolClient client = new MerchantMcpToolClient(
                restClientBuilder,
                new MerchantMcpToolProperties(5000, 5000, 15000, Duration.ofHours(1)),
                new MerchantOutboundUrlValidator(),
                ucpMcpClient()
        );

        MerchantClientHttpRequestFactory mcpRequestFactory = merchantRequestFactory(
                ReflectionTestUtils.getField(client, "restClient")
        );
        SimpleClientHttpRequestFactory sharedBuilderRequestFactory = simpleRequestFactory(restClientBuilder.build());
        RequestConfig mcpRequestConfig = ((Configurable) mcpRequestFactory.getHttpClient()).getConfig();

        assertThat(mcpRequestConfig.getConnectTimeout().toMilliseconds()).isEqualTo(5000L);
        assertThat(mcpRequestConfig.getResponseTimeout().toMilliseconds()).isEqualTo(5000L);
        assertThat(mcpRequestConfig.isRedirectsEnabled()).isFalse();
        assertThat(ReflectionTestUtils.getField(sharedBuilderRequestFactory, "connectTimeout")).isEqualTo(1234);
        assertThat(ReflectionTestUtils.getField(sharedBuilderRequestFactory, "readTimeout")).isEqualTo(5678);
    }

    @Test
    void merchantHttpClientDoesNotAutomaticallyRetryRateLimitedResponses() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/mcp", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.start();
        try {
            RestClient restClient = RestClient.builder()
                    .requestFactory(new MerchantClientHttpRequestFactory(new AllowLocalhostMerchantOutboundUrlValidator()))
                    .build();

            assertThatThrownBy(() -> restClient.post()
                    .uri("http://127.0.0.1:%d/api/mcp".formatted(server.getAddress().getPort()))
                    .retrieve()
                    .toBodilessEntity())
                    .hasMessageContaining("429");

            assertThat(requests).hasValue(1);
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void catalogCallNeverFallsBackAfterIntegrationEndpointFailure() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantMcpToolClient client = client(restClientBuilder.build(), "93.184.216.34");
        server.expect(requestTo("https://advertised.example/advertised-mcp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "content": [],
                            "isError": true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.callTool(
                new MerchantSemanticSearchResult(
                        UUID.randomUUID(),
                        "advertised.example",
                        "Merchant",
                        "https://advertised.example/advertised-mcp",
                        "https://advertised.example/profile-mcp",
                        "Context",
                        0.9d,
                        0.8d,
                        1
                ),
                "search_catalog",
                Map.of("catalog", Map.of("query", "candle"))
        ))
                .isInstanceOf(MerchantMcpToolException.class)
                .hasMessageContaining("Exact MCP tool search_catalog failed");

        server.verify();
    }

    @Test
    void exactEndpointCallNeverFansOutAfterFailure() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantMcpToolClient client = client(restClientBuilder.build(), "93.184.216.34");
        server.expect(requestTo("https://merchant.example/exact-mcp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        MerchantCartProvider provider = new MerchantCartProvider(
                UUID.randomUUID(), "merchant.example", "https://merchant.example/exact-mcp",
                "https://merchant.example/profile-mcp");

        assertThatThrownBy(() -> client.callToolExactEndpoint(
                provider, "create_cart", Map.of(), Map.of()))
                .isInstanceOf(MerchantMcpToolException.class)
                .hasMessageContaining("Exact MCP tool create_cart failed");

        server.verify();
    }

    @Test
    void exactEndpointCanReturnStructuredBusinessErrorsWithoutCandidateFallback() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantMcpToolClient client = client(restClientBuilder.build(), "93.184.216.34");
        server.expect(requestTo("https://merchant.example/exact-mcp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\\"checkout\\\":{\\\"id\\\":\\\"checkout-1\\\"}}"
                              }
                            ],
                            "structuredContent": {
                              "checkout": {
                                "id": "checkout-1"
                              }
                            },
                            "isError": true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        MerchantCartProvider provider = new MerchantCartProvider(
                UUID.randomUUID(), "merchant.example", "https://merchant.example/exact-mcp",
                "https://merchant.example/profile-mcp");

        MerchantMcpToolCallResult result = client.callToolExactEndpointReturningJsonToolErrors(
                provider, "update_checkout", Map.of(), Map.of());

        assertThat(result.endpoint()).isEqualTo("https://merchant.example/exact-mcp");
        assertThat(result.contentText()).contains("checkout-1");
        assertThat(result.structuredContent().path("checkout").path("id").stringValue())
                .isEqualTo("checkout-1");
        server.verify();
    }

    @Test
    void doesNotFallbackToNextEndpointAfterRateLimitOrLogResponsePayload(CapturedOutput output) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantMcpToolClient client = client(restClientBuilder.build(), "93.184.216.34");
        server.expect(requestTo("https://advertised.example/advertised-mcp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Bearer secret-token product-payload")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.callTool(
                new MerchantSemanticSearchResult(
                        UUID.randomUUID(),
                        "advertised.example",
                        "Merchant",
                        "https://advertised.example/advertised-mcp",
                        "https://advertised.example/profile-mcp",
                        "Context",
                        0.9d,
                        0.8d,
                        1
                ),
                "search_catalog",
                Map.of("catalog", Map.of("query", "candle"))
        ))
                .isInstanceOf(MerchantMcpToolException.class)
                .hasMessageContaining("Exact MCP tool search_catalog failed");
        server.verify();
        assertThat(output.getAll()).doesNotContain("secret-token", "product-payload", "Bearer");
    }

    private MerchantMcpToolClient client(RestClient restClient, String resolvedAddress) {
        return new MerchantMcpToolClient(
                restClient,
                MerchantOutboundUrlValidator.withResolver(host -> List.of(InetAddress.getByName(resolvedAddress)))
        );
    }

    private UcpMcpClient ucpMcpClient() {
        return new UcpMcpClient(new AgentIdentity(
                URI.create("https://agent.example/.well-known/ucp-agent.json"),
                "2026-04-08",
                "agent-key-1"
        ));
    }

    private MerchantClientHttpRequestFactory merchantRequestFactory(Object restClient) {
        Object requestFactory = ReflectionTestUtils.getField(restClient, "clientRequestFactory");
        assertThat(requestFactory).isInstanceOf(MerchantClientHttpRequestFactory.class);
        return (MerchantClientHttpRequestFactory) requestFactory;
    }

    private SimpleClientHttpRequestFactory simpleRequestFactory(Object restClient) {
        Object requestFactory = ReflectionTestUtils.getField(restClient, "clientRequestFactory");
        assertThat(requestFactory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        return (SimpleClientHttpRequestFactory) requestFactory;
    }

    private static class AllowLocalhostMerchantOutboundUrlValidator extends MerchantOutboundUrlValidator {

        @Override
        void validatePublicAddresses(List<InetAddress> addresses) {
        }
    }
}
