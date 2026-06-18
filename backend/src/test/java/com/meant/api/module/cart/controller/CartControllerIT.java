package com.meant.api.module.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.module.cart.controller.response.CartResponse;
import com.meant.api.module.cart.controller.response.CheckoutResponse;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.cart.service.CartClient;
import com.meant.api.module.cart.service.dto.CartToolResponse;
import com.meant.api.module.cart.service.dto.CartToolResult;
import com.meant.api.module.cart.service.dto.UpdateCartArguments;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartControllerIT extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private FakeCartClient cartClient;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        cartClient.reset();
        client = RestTestClient.bindToServer(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void cartEndpointsRequireAuthentication() {
        Merchant merchant = saveMerchant();

        client.post().uri("/api/carts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(createCartBody(merchant.getId()))
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(cartClient.updateCount()).isZero();
    }

    @Test
    void ownerCanReadUpdateAndCheckoutCart() {
        UUID userId = UUID.randomUUID();
        Merchant merchant = saveMerchant();

        CartResponse created = createCart(userId, merchant.getId());

        CartResponse read = client.get().uri("/api/carts/{cartId}", created.cartId())
                .headers(headers -> headers.setBearerAuth(token(userId)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(read).isNotNull();
        assertThat(read.cartId()).isEqualTo(created.cartId());

        CartResponse updated = client.patch().uri("/api/carts/{cartId}", created.cartId())
                .headers(headers -> {
                    headers.setBearerAuth(token(userId));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "addItems": [
                            {
                              "productVariantId": "gid://shopify/ProductVariant/2",
                              "quantity": 1
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(updated).isNotNull();
        assertThat(updated.cartId()).isEqualTo(created.cartId());

        CheckoutResponse checkout = client.get().uri("/api/carts/{cartId}/checkout", created.cartId())
                .headers(headers -> headers.setBearerAuth(token(userId)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CheckoutResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(checkout).isNotNull();
        assertThat(checkout.cartId()).isEqualTo(created.cartId());
        assertThat(checkout.checkoutUrl()).contains("checkout");

        Cart persisted = cartRepository.findById(created.cartId()).orElseThrow();
        assertThat(persisted.getUserId()).isEqualTo(userId);
    }

    @Test
    void nonOwnerCannotReadUpdateOrCheckoutCart() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Merchant merchant = saveMerchant();
        CartResponse created = createCart(ownerId, merchant.getId());

        client.get().uri("/api/carts/{cartId}", created.cartId())
                .headers(headers -> headers.setBearerAuth(token(otherUserId)))
                .exchange()
                .expectStatus().isNotFound();

        client.patch().uri("/api/carts/{cartId}", created.cartId())
                .headers(headers -> {
                    headers.setBearerAuth(token(otherUserId));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "addItems": [
                            {
                              "productVariantId": "gid://shopify/ProductVariant/2",
                              "quantity": 1
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isNotFound();

        client.get().uri("/api/carts/{cartId}/checkout", created.cartId())
                .headers(headers -> headers.setBearerAuth(token(otherUserId)))
                .exchange()
                .expectStatus().isNotFound();

        assertThat(cartClient.updateCount()).isEqualTo(1);
        assertThat(cartClient.getCount()).isZero();
    }

    private CartResponse createCart(UUID userId, UUID merchantId) {
        CartResponse created = client.post().uri("/api/carts")
                .headers(headers -> {
                    headers.setBearerAuth(token(userId));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(createCartBody(merchantId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        return created;
    }

    private String createCartBody(UUID merchantId) {
        return """
                {
                  "merchantId": "%s",
                  "addItems": [
                    {
                      "productVariantId": "gid://shopify/ProductVariant/1",
                      "quantity": 1
                    }
                  ]
                }
                """.formatted(merchantId);
    }

    private Merchant saveMerchant() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-16T11:05:00Z");
        String domain = "merchant-%s.example".formatted(id);
        MerchantRaw merchantRaw = merchantRawRepository.save(MerchantRaw.builder()
                .id(UUID.randomUUID())
                .datasetRowIdx(Math.abs(id.hashCode()))
                .domain(domain)
                .status("OK")
                .ucpUrl("https://merchant.example/.well-known/ucp.json")
                .httpStatus(200)
                .ucpVersion("1.0")
                .hasCheckout(true)
                .hasIdentityLinking(false)
                .hasCartManagement(true)
                .hasOrder(false)
                .hasPaymentToken(false)
                .capabilityCount(1)
                .transports("[]")
                .fetchedAt(now)
                .processed(true)
                .processingStatus("SUCCESS")
                .sourceHash("raw-hash-%s".formatted(id))
                .active(true)
                .lastSeenAt(now)
                .build());
        return merchantRepository.save(Merchant.builder()
                .id(id)
                .merchantRaw(merchantRaw)
                .domain(domain)
                .ucpUrl("https://merchant.example/.well-known/ucp.json")
                .ucpVersion("1.0")
                .advertisedMcpEndpoint("https://merchant.example/api/mcp")
                .profileHash("hash-%s".formatted(id))
                .name("Merchant")
                .description("Description")
                .about("About")
                .targetAudience("Customers")
                .profileQuestion("Question")
                .profileAnswerRaw("Answer")
                .active(true)
                .lastProfiledAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private static String token(UUID id) {
        String payload = id + "|" + id + "@example.com|Ada Lovelace";
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

        @Bean
        @Primary
        FakeCartClient testCartClient() {
            return new FakeCartClient();
        }
    }

    static class FakeCartClient extends CartClient {

        private final AtomicInteger updateCount = new AtomicInteger();
        private final AtomicInteger getCount = new AtomicInteger();
        private final AtomicInteger cartSequence = new AtomicInteger();

        FakeCartClient() {
            super(null, null);
        }

        void reset() {
            updateCount.set(0);
            getCount.set(0);
        }

        int updateCount() {
            return updateCount.get();
        }

        int getCount() {
            return getCount.get();
        }

        @Override
        public CartToolResult updateCart(MerchantCartProvider provider, UpdateCartArguments arguments) {
            updateCount.incrementAndGet();
            return cartToolResult();
        }

        @Override
        public CartToolResult getCart(MerchantCartProvider provider, String remoteCartId) {
            getCount.incrementAndGet();
            return cartToolResult();
        }

        private CartToolResult cartToolResult() {
            int sequence = cartSequence.incrementAndGet();
            return new CartToolResult(
                    "https://merchant.example/api/mcp",
                    "{}",
                    new CartToolResponse(
                            "Checkout when ready",
                            new CartToolResponse.Cart(
                                    "gid://shopify/Cart/" + sequence,
                                    Instant.parse("2026-06-16T11:05:00Z"),
                                    Instant.parse("2026-06-16T11:05:01Z"),
                                    List.of(cartLine(sequence)),
                                    new CartToolResponse.Cost(
                                            new CartToolResponse.Money("14.95", "USD"),
                                            new CartToolResponse.Money("14.95", "USD")
                                    ),
                                    1,
                                    "https://merchant.example/checkout/" + sequence,
                                    List.of(),
                                    List.of(),
                                    List.of(),
                                    List.of(),
                                    List.of()
                            ),
                            List.of()
                    )
            );
        }

        private CartToolResponse.Line cartLine(int sequence) {
            return new CartToolResponse.Line(
                    "gid://shopify/CartLine/" + sequence,
                    1,
                    new CartToolResponse.Cost(
                            new CartToolResponse.Money("14.95", "USD"),
                            new CartToolResponse.Money("14.95", "USD")
                    ),
                    new CartToolResponse.Merchandise(
                            "gid://shopify/ProductVariant/" + sequence,
                            "3x6",
                            new CartToolResponse.Product("gid://shopify/Product/" + sequence, "Candle")
                    )
            );
        }
    }
}
