package com.meant.api.module.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.controller.response.CartResponse;
import com.meant.api.module.cart.controller.response.CheckoutResponse;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.cart.cancel.dto.CancelCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.module.cart.service.MerchantCartPluginDispatchService;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.module.checkout.service.MerchantCheckoutPluginDispatchService;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.support.UcpSession;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartControllerIT extends PostgresIntegrationTestSupport {

    @LocalServerPort
    private int port;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private FakeCartDispatchService cartDispatchService;

    @Autowired
    private FakeCheckoutDispatchService checkoutDispatchService;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        cartDispatchService.reset();
        checkoutDispatchService.reset();
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

        assertThat(cartDispatchService.createCount()).isZero();
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
        assertThat(updated.deliveryGroups()).hasSize(1);
        assertThat(updated.deliveryGroups().getFirst().deliveryOptions()).extracting("handle")
                .containsExactly("standard", "express");

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
        assertThat(checkout.continueUrl()).contains("continue");
        assertThat(checkout.nextAction()).isEqualTo(CheckoutNextAction.UNKNOWN);
        assertThat(checkoutDispatchService.createCount()).isEqualTo(1);

        Cart persisted = cartRepository.findById(created.cartId()).orElseThrow();
        assertThat(persisted.getUserId()).isEqualTo(userId);

        client.delete().uri("/api/carts/{cartId}", created.cartId())
                .headers(headers -> headers.setBearerAuth(token(userId)))
                .exchange()
                .expectStatus().isNoContent();

        client.get().uri("/api/carts/{cartId}", created.cartId())
                .headers(headers -> headers.setBearerAuth(token(userId)))
                .exchange()
                .expectStatus().isNotFound();
        assertThat(cartDispatchService.cancelCount()).isEqualTo(1);
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

        assertThat(cartDispatchService.createCount()).isEqualTo(1);
        assertThat(cartDispatchService.updateCount()).isZero();
        assertThat(cartDispatchService.getCount()).isZero();
        assertThat(checkoutDispatchService.createCount()).isZero();
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
        FakeCartDispatchService testCartDispatchService() {
            return new FakeCartDispatchService();
        }

        @Bean
        @Primary
        FakeCheckoutDispatchService testCheckoutDispatchService() {
            return new FakeCheckoutDispatchService();
        }
    }

    static class FakeCartDispatchService extends MerchantCartPluginDispatchService {

        private final AtomicInteger createCount = new AtomicInteger();
        private final AtomicInteger updateCount = new AtomicInteger();
        private final AtomicInteger getCount = new AtomicInteger();
        private final AtomicInteger cancelCount = new AtomicInteger();
        private final AtomicInteger cartSequence = new AtomicInteger();

        FakeCartDispatchService() {
            super(null, null, null);
        }

        void reset() {
            createCount.set(0);
            updateCount.set(0);
            getCount.set(0);
            cancelCount.set(0);
        }

        int createCount() {
            return createCount.get();
        }

        int updateCount() {
            return updateCount.get();
        }

        int getCount() {
            return getCount.get();
        }

        int cancelCount() {
            return cancelCount.get();
        }

        @Override
        public UcpCartToolResult createCart(
                MerchantCartProvider provider,
                CreateCartRequest request,
                UcpSession session
        ) {
            createCount.incrementAndGet();
            return cartToolResult();
        }

        @Override
        public UcpCartToolResult updateCart(
                MerchantCartProvider provider,
                UpdateCartRequest request,
                UcpSession session
        ) {
            updateCount.incrementAndGet();
            return cartToolResult();
        }

        @Override
        public UcpCartToolResult getCart(
                MerchantCartProvider provider,
                GetCartRequest request,
                UcpSession session
        ) {
            getCount.incrementAndGet();
            return cartToolResult();
        }

        @Override
        public CancelCartResponse cancelCart(
                MerchantCartProvider provider,
                CancelCartRequest request,
                UcpSession session
        ) {
            cancelCount.incrementAndGet();
            return new CancelCartResponse(request.cartId(), "canceled", true, List.of(), List.of());
        }

        private UcpCartToolResult cartToolResult() {
            int sequence = cartSequence.incrementAndGet();
            UcpCartResponse response = new UcpCartResponse(
                    "Checkout when ready",
                    new UcpCartResponse.Cart(
                            "gid://shopify/Cart/" + sequence,
                            Instant.parse("2026-06-16T11:05:00Z"),
                            Instant.parse("2026-06-16T11:05:01Z"),
                            null,
                            List.of(cartLine(sequence)),
                            new UcpCartResponse.Cost(
                                    new UcpCartResponse.Money("14.95", "USD"),
                                    new UcpCartResponse.Money("14.95", "USD")
                            ),
                            1,
                            null,
                            null,
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(deliveryGroup()),
                            List.of()
                    ),
                    List.of(),
                    List.of()
            );
            return new UcpCartToolResult(
                    "https://merchant.example/api/mcp",
                    raw(response),
                    response
            );
        }

        private UcpCartResponse.Line cartLine(int sequence) {
            return new UcpCartResponse.Line(
                    "gid://shopify/CartLine/" + sequence,
                    1,
                    new UcpCartResponse.Cost(
                            new UcpCartResponse.Money("14.95", "USD"),
                            new UcpCartResponse.Money("14.95", "USD")
                    ),
                    new UcpCartResponse.Merchandise(
                            "gid://shopify/ProductVariant/" + sequence,
                            "3x6",
                            new UcpCartResponse.Product("gid://shopify/Product/" + sequence, "Candle")
                    )
            );
        }

        private UcpCartResponse.DeliveryGroup deliveryGroup() {
            UcpCartResponse.DeliveryOption standard = new UcpCartResponse.DeliveryOption(
                    "standard",
                    "Standard",
                    "Arrives in 3 to 5 business days",
                    null,
                    new UcpCartResponse.Money("5.00", "USD"),
                    null,
                    "shipping",
                    "3 to 5 business days",
                    null,
                    null,
                    true
            );
            UcpCartResponse.DeliveryOption express = new UcpCartResponse.DeliveryOption(
                    "express",
                    "Express",
                    "Arrives in 1 to 2 business days",
                    null,
                    new UcpCartResponse.Money("12.00", "USD"),
                    null,
                    "shipping",
                    "1 to 2 business days",
                    null,
                    null,
                    false
            );
            return new UcpCartResponse.DeliveryGroup(
                    "delivery-group-1",
                    "delivery-group-handle-1",
                    List.of(standard, express),
                    standard
            );
        }

        private String raw(UcpCartResponse response) {
            try {
                return new ObjectMapper().writeValueAsString(response);
            } catch (JacksonException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    static class FakeCheckoutDispatchService extends MerchantCheckoutPluginDispatchService {

        private final AtomicInteger createCount = new AtomicInteger();

        FakeCheckoutDispatchService() {
            super(null, null, null);
        }

        void reset() {
            createCount.set(0);
        }

        int createCount() {
            return createCount.get();
        }

        @Override
        public UcpCheckoutToolResult createCheckout(
                MerchantCartProvider provider,
                CreateCheckoutRequest request,
                UcpSession session
        ) {
            createCount.incrementAndGet();
            UcpCheckoutResponse response = new UcpCheckoutResponse(
                    null,
                    "Open checkout in browser",
                    new UcpCheckoutResponse.Checkout(
                            "gid://shopify/Checkout/" + createCount.get(),
                            request.cartId(),
                            "open",
                            "https://merchant.example/checkout/" + createCount.get(),
                            "https://merchant.example/continue/" + createCount.get(),
                            null,
                            null,
                            Instant.parse("2026-06-16T11:06:00Z"),
                            Instant.parse("2026-06-16T11:06:01Z"),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            List.of()
                    ),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of()
            );
            return new UcpCheckoutToolResult(
                    "https://merchant.example/api/mcp",
                    raw(response),
                    response
            );
        }

        private String raw(UcpCheckoutResponse response) {
            try {
                return new ObjectMapper().writeValueAsString(response);
            } catch (JacksonException exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
