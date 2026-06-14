package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.service.dto.UcpProfile;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UcpProfileClientTest {

    @Test
    void fetchProfileRetriesWwwVariantWhenOriginalResponseIsEmpty() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(once(), requestTo("https://reebok.com/.well-known/ucp"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://www.reebok.com/.well-known/ucp"))
                .andRespond(withSuccess(profileResponse(), MediaType.APPLICATION_JSON));
        UcpProfileClient client = new UcpProfileClient(restClientBuilder, new ObjectMapper());

        UcpProfile profile = client.fetchProfile("https://reebok.com/.well-known/ucp");

        assertThat(profile.version()).isEqualTo("2026-04-08");
        server.verify();
    }

    @Test
    void fetchProfileRetriesJsonVariantWhenOriginalAndWwwAreNotUcpJson() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(once(), requestTo("https://cupshe.com/.well-known/ucp"))
                .andRespond(withSuccess("<html>not ucp</html>", MediaType.TEXT_HTML));
        server.expect(once(), requestTo("https://www.cupshe.com/.well-known/ucp"))
                .andRespond(withResourceNotFound().body("<html>not found</html>"));
        server.expect(once(), requestTo("https://cupshe.com/.well-known/ucp.json"))
                .andRespond(withSuccess(profileResponse(), MediaType.APPLICATION_JSON));
        UcpProfileClient client = new UcpProfileClient(restClientBuilder, new ObjectMapper());

        UcpProfile profile = client.fetchProfile("https://cupshe.com/.well-known/ucp");

        assertThat(profile.version()).isEqualTo("2026-04-08");
        server.verify();
    }

    private String profileResponse() {
        return """
                {
                  "ucp": {
                    "version": "2026-04-08",
                    "supported_versions": {
                      "2026-04-08": "https://ucp.dev/2026-04-08"
                    },
                    "services": {
                      "dev.ucp.shopping": [
                        {
                          "version": "2026-04-08",
                          "transport": "mcp",
                          "endpoint": "https://reebok.com/api/mcp"
                        }
                      ]
                    }
                  }
                }
                """;
    }
}
