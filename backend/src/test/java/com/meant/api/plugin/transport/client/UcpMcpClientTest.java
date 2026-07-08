package com.meant.api.plugin.transport.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UcpMcpClientTest {

    @Test
    void callToolSendsAgentProfileMetaAndExtractsStructuredContentNegotiation() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        UcpMcpClient client = new UcpMcpClient(identity(), new ObjectMapper());
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"method\":\"tools/call\"")))
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.params.name").value("search_catalog"))
                .andExpect(jsonPath("$.params.arguments.catalog.query").value("jacket"))
                .andExpect(jsonPath("$.params.arguments.meta['ucp-agent'].profile")
                        .value("https://agent.example/.well-known/ucp-agent.json"))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"ok\\":true}"
                              }
                            ],
                            "structuredContent": {
                              "catalog": {
                                "count": 1
                              },
                              "ucp": {
                                "capabilities": [
                                  {
                                    "id": "dev.ucp.shopping.catalog.search",
                                    "version": "2026-04-08"
                                  }
                                ]
                              }
                            },
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        UcpToolResponse response = client.callTool(
                restClientBuilder.build(),
                URI.create("https://merchant.example/api/mcp"),
                "search_catalog",
                Map.of("catalog", Map.of("query", "jacket"))
        );

        CapabilityId catalogSearch = CapabilityId.of("dev.ucp.shopping.catalog.search");
        assertThat(response.textContent()).isEqualTo("{\"ok\":true}");
        assertThat(response.structuredContent()).isInstanceOf(Map.class);
        assertThat(response.negotiatedCapabilities().supports(catalogSearch)).isTrue();
        assertThat(response.negotiatedCapabilities().version(catalogSearch)).contains("2026-04-08");
        server.verify();
    }

    @Test
    void listToolsSendsAgentProfileMetaAndReturnsResultJson() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        UcpMcpClient client = new UcpMcpClient(identity(), new ObjectMapper());
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"method\":\"tools/list\"")))
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.params.name").doesNotExist())
                .andExpect(jsonPath("$.params.arguments.meta['ucp-agent'].profile")
                        .value("https://agent.example/.well-known/ucp-agent.json"))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "tools": [
                              {
                                "name": "search_catalog"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        String toolsList = client.listTools(
                restClientBuilder.build(),
                URI.create("https://merchant.example/api/mcp")
        );

        assertThat(toolsList).contains("\"tools\"");
        assertThat(toolsList).contains("\"search_catalog\"");
        server.verify();
    }

    @Test
    void callToolAllowingJsonToolErrorsReturnsJsonPayload() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        UcpMcpClient client = new UcpMcpClient(identity(), new ObjectMapper());
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"method\":\"tools/call\"")))
                .andExpect(jsonPath("$.params.name").value("create_checkout"))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"id\\":\\"checkout_1\\",\\"status\\":\\"incomplete\\"}"
                              }
                            ],
                            "isError": true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        UcpToolResponse response = client.callToolAllowingJsonToolErrors(
                restClientBuilder.build(),
                URI.create("https://merchant.example/api/mcp"),
                "create_checkout",
                Map.of("checkout", Map.of("cart_id", "cart_1")),
                Map.of()
        );

        assertThat(response.textContent()).contains("\"checkout_1\"");
        server.verify();
    }

    @Test
    void callToolAllowingJsonToolErrorsReturnsStructuredPayloadWhenTextIsPlain() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        UcpMcpClient client = new UcpMcpClient(identity(), new ObjectMapper());
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "Checkout requires more information"
                              }
                            ],
                            "structuredContent": {
                              "id": "checkout_1",
                              "status": "incomplete"
                            },
                            "isError": true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        UcpToolResponse response = client.callToolAllowingJsonToolErrors(
                restClientBuilder.build(),
                URI.create("https://merchant.example/api/mcp"),
                "create_checkout",
                Map.of("checkout", Map.of("cart_id", "cart_1")),
                Map.of()
        );

        assertThat(response.textContent()).isNull();
        assertThat(response.structuredContent()).isInstanceOf(Map.class);
        server.verify();
    }

    @Test
    void callToolAllowingJsonToolErrorsStillRejectsPlainTextToolErrorsWithMetadataOnlyStructuredContent() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        UcpMcpClient client = new UcpMcpClient(identity(), new ObjectMapper());
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "Missing required arguments: checkout"
                              }
                            ],
                            "structuredContent": {
                              "ucp": {
                                "capabilities": {
                                  "dev.ucp.shopping.checkout.create": "2026-04-08"
                                }
                              }
                            },
                            "isError": true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.callToolAllowingJsonToolErrors(
                restClientBuilder.build(),
                URI.create("https://merchant.example/api/mcp"),
                "create_checkout",
                Map.of("cart_id", "cart_1"),
                Map.of()
        ))
                .isInstanceOf(UcpMcpException.class)
                .hasMessageContaining("Missing required arguments: checkout");
        server.verify();
    }

    private AgentIdentity identity() {
        return new AgentIdentity(
                URI.create("https://agent.example/.well-known/ucp-agent.json"),
                "2026-04-08",
                "agent-key-1"
        );
    }
}
