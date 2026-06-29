package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.exception.MerchantMcpToolException;
import com.meant.api.module.merchant.properties.MerchantMcpToolProperties;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import com.meant.api.plugin.transport.client.UcpMcpClient;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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
                .hasMessageContaining("failed for all endpoint candidates");
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
    void fallsBackToAdvertisedEndpointWhenProfileEndpointFails() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantMcpToolClient client = client(restClientBuilder.build(), "93.184.216.34");
        server.expect(requestTo("https://advertised.example/profile-mcp"))
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
        server.expect(requestTo("https://advertised.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 2,
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
                        "https://advertised.example/profile-mcp",
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
}
