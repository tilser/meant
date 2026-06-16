package com.meant.api.module.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.module.user.controller.response.UserResponse;
import com.meant.api.module.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boots the full app on a random port and drives it over HTTP. A test {@link JwtDecoder} turns
 * opaque bearer tokens of the form {@code <userId>|<email>|<fullName>} into a {@link Jwt}, so we can
 * exercise the resource-server filter chain without standing up Supabase or signing real tokens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerIT extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        // JDK request factory supports PATCH (the default JDK client does not via RestTemplate).
        client = RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    /**
     * Builds an opaque bearer token whose value is a base64url-encoded {@code id|email|fullName}
     * payload. Base64url uses only characters allowed in an RFC 6750 bearer token, so the token
     * survives the {@code BearerTokenAuthenticationFilter} before reaching {@link #testJwtDecoder()}.
     */
    private static String token(UUID id, String email, String fullName) {
        String payload = id + "|" + email + "|" + (fullName == null ? "" : fullName);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            return token -> {
                String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|", -1);
                if (parts.length < 2) {
                    throw new JwtException("malformed test token");
                }
                Jwt.Builder builder = Jwt.withTokenValue(token)
                        .header("alg", "none")
                        .subject(parts[0])
                        .claim("email", parts[1])
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600));
                if (parts.length >= 3 && !parts[2].isBlank()) {
                    builder.claim("user_metadata", Map.of("full_name", parts[2]));
                }
                return builder.build();
            };
        }
    }

    @Test
    void meWithoutTokenReturns401() {
        client.get().uri("/api/users/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void meWithTokenCreatesAndReturnsUser() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";

        UserResponse body = client.get().uri("/api/users/me")
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(id);
        assertThat(body.email()).isEqualTo(email);
        assertThat(body.firstName()).isEqualTo("Ada");
        assertThat(body.surname()).isEqualTo("Lovelace");
        assertThat(userRepository.findById(id)).isPresent();
    }

    @Test
    void patchUpdatesProfile() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";

        UserResponse body = client.patch().uri("/api/users/me")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, null));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("{\"firstName\":\"Augusta\",\"surname\":\"Byron\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.firstName()).isEqualTo("Augusta");
        assertThat(body.surname()).isEqualTo("Byron");
    }

    @Test
    void publicHealthEndpointStaysOpen() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
