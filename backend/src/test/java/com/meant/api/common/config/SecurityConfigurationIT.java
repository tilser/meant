package com.meant.api.common.config;

import com.meant.api.PostgresIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigurationIT extends PostgresIntegrationTestSupport {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void cartEndpointsRequireAuthentication() {
        UUID cartId = UUID.randomUUID();

        client.post().uri("/api/carts")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().isUnauthorized();

        client.get().uri("/api/carts/{cartId}", cartId)
                .exchange()
                .expectStatus().isUnauthorized();

        client.patch().uri("/api/carts/{cartId}", cartId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().isUnauthorized();

        client.get().uri("/api/carts/{cartId}/checkout", cartId)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void discoverConversationPutPreflightAllowsLocalFrontend() {
        UUID conversationId = UUID.randomUUID();

        client.method(HttpMethod.OPTIONS)
                .uri("/api/users/me/discover/conversations/{conversationId}", conversationId)
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, value -> {
                    org.assertj.core.api.Assertions.assertThat(value.split(","))
                            .contains("PUT");
                })
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, value -> {
                    org.assertj.core.api.Assertions.assertThat(value.toLowerCase())
                            .contains("authorization", "content-type");
                });
    }

    @Test
    void merchantEndpointsRequireAuthentication() {
        client.get().uri("/api/merchants")
                .exchange()
                .expectStatus().isUnauthorized();

        client.post().uri("/api/merchants/semantic-search")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().isUnauthorized();

        client.post().uri("/api/merchants/semantic-product-search")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void healthEndpointStaysPublic() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void agentProfileEndpointStaysPublic() {
        client.get().uri("/.well-known/ucp-agent.json")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "max-age=300, public")
                .expectBody()
                .jsonPath("$.ucp.version").isEqualTo("2026-04-08")
                .jsonPath("$.ucp.services['dev.ucp.shopping'][0].transport").isEqualTo("mcp")
                .jsonPath("$.ucp.capabilities['dev.ucp.shopping.catalog.search'][0].version")
                .isEqualTo("2026-04-08")
                .jsonPath("$.ucp.capabilities['dev.ucp.shopping.catalog.lookup'][0].version")
                .isEqualTo("2026-04-08")
                .jsonPath("$.ucp.capabilities['dev.shopify.catalog.global'][0].version")
                .isEqualTo("2026-04-08")
                .jsonPath("$.ucp.payment_handlers").isMap()
                .jsonPath("$.profile_url").doesNotExist()
                .jsonPath("$.protocol_version").doesNotExist();
    }
}
