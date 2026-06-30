package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.service.dto.MerchantMcpProfileResult;
import com.meant.api.module.merchant.service.dto.StorePolicyFaqEntry;
import com.meant.api.plugin.transport.client.UcpMcpClient;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MerchantDomainMcpClientTest {

    @Test
    void parsesMcpContentTextIntoStorePolicyFaqEntry() {
        MerchantDomainMcpClient client = new MerchantDomainMcpClient(RestClient.builder(), new ObjectMapper());

        StorePolicyFaqEntry entry = client.parseStorePolicyFaqContent("""
                [{"question":"Tell me about your store?","answer":"Description: Clothes\\nAbout us: We sell clothes\\nTarget audience: Adults\\nCategories: Apparel, Shoes\\nPopular searches: jeans, dresses"}]
                """);

        assertThat(entry.question()).isEqualTo("Tell me about your store?");
        assertThat(entry.answer()).contains("Description: Clothes");
    }

    @Test
    void fetchStoreProfileSendsAgentProfileMeta() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ObjectMapper objectMapper = new ObjectMapper();
        MerchantDomainMcpClient client = new MerchantDomainMcpClient(
                restClientBuilder.build(),
                objectMapper,
                MerchantOutboundUrlValidator.withResolver(host -> List.of(InetAddress.getByName("93.184.216.34"))),
                new UcpMcpClient(new AgentIdentity(
                        URI.create("https://agent.example/.well-known/ucp-agent.json"),
                        "2026-04-08",
                        "agent-key-1"
                ), objectMapper)
        );
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.params.name").value("search_shop_policies_and_faqs"))
                .andExpect(jsonPath("$.params.arguments.query").value("Tell me about your store?"))
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
                                "text": "[{\\"question\\":\\"Tell me about your store?\\",\\"answer\\":\\"Description: Merchant\\"}]"
                              }
                            ],
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        MerchantMcpProfileResult result = client.fetchStoreProfile("merchant.example");

        assertThat(result.endpoint()).isEqualTo("https://merchant.example/api/mcp");
        assertThat(result.entry().answer()).contains("Description: Merchant");
        server.verify();
    }
}
