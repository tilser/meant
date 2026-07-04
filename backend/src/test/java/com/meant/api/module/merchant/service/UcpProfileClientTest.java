package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.exception.MerchantEnrichmentException;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpProfileFetchResult;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
        UcpProfileClient client = client(restClientBuilder, "93.184.216.34");

        UcpProfile profile = client.fetchProfile("reebok.com", "https://reebok.com/.well-known/ucp");

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
        UcpProfileClient client = client(restClientBuilder, "93.184.216.34");

        UcpProfile profile = client.fetchProfile("cupshe.com", "https://cupshe.com/.well-known/ucp");

        assertThat(profile.version()).isEqualTo("2026-04-08");
        server.verify();
    }

    @Test
    void fetchProfileDoesNotRetryVariantsAfterRateLimit() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(once(), requestTo("https://reebok.com/.well-known/ucp"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        UcpProfileClient client = client(restClientBuilder, "93.184.216.34");

        assertThatThrownBy(() -> client.fetchProfile("reebok.com", "https://reebok.com/.well-known/ucp"))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("rate limited");
        server.verify();
    }

    @Test
    void fetchProfileResultPreservesUnknownProfileFieldsInRawArchive() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(once(), requestTo("https://allbirds.com/.well-known/ucp"))
                .andRespond(withSuccess(profileResponse(), MediaType.APPLICATION_JSON));
        UcpProfileClient client = client(restClientBuilder, "93.184.216.34");

        UcpProfileFetchResult result = client.fetchProfileResult("allbirds.com", "https://allbirds.com/.well-known/ucp");

        assertThat(result.profile().version()).isEqualTo("2026-04-08");
        assertThat(result.rawProfile()).contains("\"x-merchant-extension\"");
        assertThat(result.rawProfile()).doesNotContain("\"ucp\"");
        assertThat(result.endpoint()).isEqualTo("https://allbirds.com/.well-known/ucp");
        assertThat(result.capturedAt()).isNotNull();
        server.verify();
    }

    @Test
    void fetchProfileResultRejectsNullProfileBodyWithoutNullPointerException() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(once(), requestTo("https://www.nullprofile.example/.well-known/ucp.json"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        UcpProfileClient client = client(restClientBuilder, "93.184.216.34");

        assertThatThrownBy(() -> client.fetchProfileResult(
                "www.nullprofile.example",
                "https://www.nullprofile.example/.well-known/ucp.json"
        ))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessage("UCP profile response did not contain ucp data");
        server.verify();
    }

    @Test
    void blocksProfileUrlResolvingToLocalAddress() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        UcpProfileClient client = client(restClientBuilder, "127.0.0.1");

        assertThatThrownBy(() -> client.fetchProfile("localhost", "https://localhost/.well-known/ucp"))
                .isInstanceOf(MerchantEnrichmentException.class)
                .hasMessageContaining("Blocked UCP profile URL");
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
                    },
                    "x-merchant-extension": {
                      "tier": "gold"
                    }
                  }
                }
                """;
    }

    private UcpProfileClient client(RestClient.Builder restClientBuilder, String resolvedAddress) {
        return new UcpProfileClient(
                restClientBuilder.build(),
                new ObjectMapper(),
                MerchantOutboundUrlValidator.withResolver(host -> List.of(InetAddress.getByName(resolvedAddress)))
        );
    }
}
