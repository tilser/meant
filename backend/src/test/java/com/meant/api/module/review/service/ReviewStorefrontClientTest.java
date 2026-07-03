package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class ReviewStorefrontClientTest {

    @Test
    void fetchReturnsHtmlWithinConfiguredSizeLimit() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ReviewStorefrontClient client = new ReviewStorefrontClient(
                restClientBuilder.build(),
                publicHostValidator(),
                32
        );
        server.expect(requestTo("https://merchant.example/"))
                .andRespond(withSuccess("<html>ok</html>", MediaType.TEXT_HTML));

        assertThat(client.fetch(URI.create("https://merchant.example/")).html()).isEqualTo("<html>ok</html>");
        server.verify();
    }

    @Test
    void fetchRejectsHtmlBeyondConfiguredSizeLimit() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        ReviewStorefrontClient client = new ReviewStorefrontClient(
                restClientBuilder.build(),
                publicHostValidator(),
                8
        );
        server.expect(requestTo("https://merchant.example/"))
                .andRespond(withSuccess("<html>too large</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> client.fetch(URI.create("https://merchant.example/")))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("Storefront response exceeded 8 bytes");
        server.verify();
    }

    private ReviewOutboundUrlValidator publicHostValidator() {
        return ReviewOutboundUrlValidator.withResolver(_ -> List.of(InetAddress.getByName("93.184.216.34")));
    }
}
