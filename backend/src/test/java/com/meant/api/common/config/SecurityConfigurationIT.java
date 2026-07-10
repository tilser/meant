package com.meant.api.common.config;

import com.meant.api.PostgresIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
                .expectBody()
                .jsonPath("$.profile_url").isEqualTo("http://localhost:8080/.well-known/ucp-agent.json")
                .jsonPath("$.protocol_version").isEqualTo("2026-04-08")
                .jsonPath("$.signing_key_id").isEqualTo("meant-test");
    }
}
