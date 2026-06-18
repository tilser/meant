package com.meant.api.common.config;

import com.meant.api.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.task.scheduling.enabled=false"
)
class SecurityConfigurationIT extends PostgresIntegrationTest {

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
}
