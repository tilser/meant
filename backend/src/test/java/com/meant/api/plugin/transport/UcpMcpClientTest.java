package com.meant.api.plugin.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.plugin.spi.CapabilityId;
import com.meant.api.plugin.spi.UcpToolResponse;
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
                .andExpect(content().string(containsString("\"meta\"")))
                .andExpect(content().string(containsString("\"ucp-agent\"")))
                .andExpect(content().string(containsString("\"profile\":\"https://agent.example/.well-known/ucp-agent.json\"")))
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
                .andExpect(content().string(containsString("\"profile\":\"https://agent.example/.well-known/ucp-agent.json\"")))
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

    private AgentIdentity identity() {
        return new AgentIdentity(
                URI.create("https://agent.example/.well-known/ucp-agent.json"),
                "2026-04-08",
                "agent-key-1"
        );
    }
}
