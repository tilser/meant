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
import com.meant.api.module.cart.service.CartOfferRevalidationService;
import com.meant.api.module.cart.service.SelectedOfferCartRoutingService;
import com.meant.api.module.cart.service.CartBindingMetrics;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.service.dto.CartToolCallContext;
import com.meant.api.module.checkout.constant.CheckoutAttributionRail;
import com.meant.api.module.checkout.constant.CheckoutAttributionTrigger;
import com.meant.api.module.checkout.constant.EmbeddedCheckoutSessionStatus;
import com.meant.api.module.checkout.entity.EmbeddedCheckoutSession;
import com.meant.api.module.checkout.repository.CheckoutPurchaseAttributionRepository;
import com.meant.api.module.checkout.repository.EmbeddedCheckoutSessionRepository;
import com.meant.api.module.checkout.service.EmbeddedCheckoutSessionStore;
import com.meant.api.module.checkout.service.command.CreateEmbeddedCheckoutSessionCommand;
import com.meant.api.module.checkout.service.dto.EmbeddedCheckoutSessionBinding;
import com.meant.api.module.user.service.UserSelectedOfferResolutionService;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.module.user.service.query.ResolveUserSelectedOfferQuery;
import com.meant.api.module.user.service.query.ResolveUserSelectedOffersQuery;
import com.meant.api.module.user.repository.UserInventoryItemRepository;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.module.checkout.service.MerchantCheckoutPluginDispatchService;
import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.support.UcpSession;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartControllerIT extends PostgresIntegrationTestSupport {

    private static final String EMBEDDED_ORIGIN = "http://localhost:3000";
    private static final String OTHER_ALLOWED_ORIGIN = "http://127.0.0.1:3000";

    @LocalServerPort
    private int port;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private EmbeddedCheckoutSessionStore embeddedCheckoutSessionStore;

    @Autowired
    private EmbeddedCheckoutSessionRepository embeddedCheckoutSessionRepository;

    @Autowired
    private CheckoutPurchaseAttributionRepository checkoutPurchaseAttributionRepository;

    @Autowired
    private UserInventoryItemRepository userInventoryItemRepository;

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
    void openApiCartContractExposesOnlyServerIssuedOfferSelection() throws JacksonException {
        String document = client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        JsonNode openApi = new ObjectMapper().readTree(document);
        JsonNode schemas = openApi.path("components").path("schemas");
        JsonNode createProperties = schemas.path("CartCreateRequest").path("properties");
        JsonNode addProperties = schemas.path("CartAddItemRequest").path("properties");
        JsonNode buyerProperties = schemas.path("CartBuyerIdentityRequest").path("properties");
        JsonNode addressProperties = schemas.path("CartDeliveryAddressSelectionRequest").path("properties");
        JsonNode deliveryOptionProperties = schemas.path("CartDeliveryOptionSelectionRequest").path("properties");
        JsonNode savedOfferProperties = schemas.path("UserSavedProductOffer").path("properties");
        JsonNode savedDetailProperties = schemas.path("UserSavedProductDetails").path("properties");
        JsonNode cartRequired = schemas.path("CartResponse").path("required");
        JsonNode lineRequired = schemas.path("CartLineResponse").path("required");
        JsonNode embeddedProperties = schemas.path("EmbeddedCheckoutBootstrapResponse").path("properties");
        JsonNode checkoutProperties = schemas.path("CheckoutResponse").path("properties");
        JsonNode savedCheckoutBuyerProperties = schemas.path("SavedCheckoutBuyerResponse").path("properties");
        JsonNode savedCheckoutAddressProperties = schemas.path("SavedCheckoutShippingAddressResponse")
                .path("properties");
        JsonNode savedCheckoutDetailsProperties = schemas.path("SavedCheckoutDetailsResponse").path("properties");
        JsonNode inventoryProperties = schemas.path("UserInventoryItemResponse").path("properties");
        JsonNode inventoryRequired = schemas.path("UserInventoryItemResponse").path("required");
        JsonNode inventoryCreateProperties = schemas.path("AddUserInventoryItemRequest").path("properties");
        JsonNode inventoryCreateRequired = schemas.path("AddUserInventoryItemRequest").path("required");
        JsonNode inventoryUpdateProperties = schemas.path("UpdateUserInventoryItemRequest").path("properties");
        JsonNode commerceReferenceProperties = schemas.path("UserInventoryCommerceReferenceResponse")
                .path("properties");
        JsonNode selectedOptionProperties = schemas.path("UserInventorySelectedOptionResponse").path("properties");
        JsonNode openedOperation = openApi.path("paths")
                .path("/api/carts/{cartId}/checkout/embedded/{sessionId}/opened")
                .path("post");

        assertThat(createProperties.has("merchantId")).isFalse();
        assertThat(createProperties.has("merchantDomain")).isFalse();
        assertThat(addProperties.has("offerKey")).isTrue();
        assertThat(createProperties.path("buyerIdentity").path("$ref").asText())
                .endsWith("/CartBuyerIdentityRequest");
        assertThat(createProperties.path("deliveryAddressesToAdd").path("items").path("$ref").asText())
                .endsWith("/CartDeliveryAddressSelectionRequest");
        assertThat(createProperties.path("selectedDeliveryOptions").path("items").path("$ref").asText())
                .endsWith("/CartDeliveryOptionSelectionRequest");
        assertThat(buyerProperties.has("email") && buyerProperties.has("phoneNumber")
                && buyerProperties.has("firstName") && buyerProperties.has("lastName")).isTrue();
        assertThat(addressProperties.has("methodId") && addressProperties.has("streetAddress")
                && addressProperties.has("addressLocality") && addressProperties.has("addressRegion")
                && addressProperties.has("postalCode") && addressProperties.has("addressCountry")).isTrue();
        assertThat(deliveryOptionProperties.has("methodId") && deliveryOptionProperties.has("groupId")
                && deliveryOptionProperties.has("selectedOptionId")).isTrue();
        assertThat(addProperties.path("offerKey").path("description").asText())
                .contains("live product session", "durable saved-product selection");
        assertThat(openApi.path("paths").path("/api/carts").path("post").path("description").asText())
                .contains("live catalog session", "durable saved-product selection");
        assertThat(savedOfferProperties.path("offerKey").path("description").asText())
                .contains("freshly rehydrated saved offer");
        assertThat(savedDetailProperties.path("ratingScore").path("format").asText()).isEqualTo("double");
        assertThat(savedDetailProperties.path("ratingScaleMax").path("format").asText()).isEqualTo("double");
        assertThat(savedDetailProperties.path("reviewCount").path("format").asText()).isEqualTo("int64");
        assertThat(addProperties.has("productVariantId")).isFalse();
        assertThat(cartRequired.toString()).doesNotContain(
                "checkoutUrl", "totalAmount", "subtotalAmount", "currency", "expiresAt");
        assertThat(lineRequired.toString()).doesNotContain(
                "productTitle", "variantTitle", "totalAmount", "subtotalAmount", "currency");
        assertThat(embeddedProperties.has("sessionId")).isTrue();
        assertThat(embeddedProperties.has("checkoutUrl")).isTrue();
        assertThat(embeddedProperties.has("checkoutAttemptId")).isTrue();
        assertThat(checkoutProperties.has("checkoutAttemptId")).isTrue();
        assertThat(checkoutProperties.path("savedCheckoutDetails").path("$ref").asText())
                .endsWith("/SavedCheckoutDetailsResponse");
        assertThat(savedCheckoutDetailsProperties.path("buyer").path("$ref").asText())
                .endsWith("/SavedCheckoutBuyerResponse");
        assertThat(savedCheckoutDetailsProperties.path("shippingAddress").path("$ref").asText())
                .endsWith("/SavedCheckoutShippingAddressResponse");
        assertThat(savedCheckoutBuyerProperties.has("email")
                && savedCheckoutBuyerProperties.has("firstName")
                && savedCheckoutBuyerProperties.has("lastName")
                && savedCheckoutBuyerProperties.has("phoneNumber")).isTrue();
        assertThat(savedCheckoutAddressProperties.has("streetAddress")
                && savedCheckoutAddressProperties.has("extendedAddress")
                && savedCheckoutAddressProperties.has("addressLocality")
                && savedCheckoutAddressProperties.has("addressRegion")
                && savedCheckoutAddressProperties.has("postalCode")
                && savedCheckoutAddressProperties.has("addressCountry")).isTrue();
        assertThat(inventoryProperties.has("commerceReference")).isTrue();
        assertThat(inventoryProperties.has("sourceCheckoutAttemptId")).isTrue();
        assertThat(inventoryProperties.has("photoPath")).isTrue();
        assertThat(inventoryProperties.has("purchasedOn")).isTrue();
        assertThat(inventoryProperties.has("size")).isTrue();
        assertThat(inventoryProperties.has("color")).isTrue();
        assertThat(inventoryProperties.has("material")).isTrue();
        assertThat(inventoryCreateRequired.toString()).contains("photoPath", "name", "category");
        assertThat(inventoryCreateRequired.toString()).doesNotContain("attributes");
        assertThat(inventoryRequired.toString()).doesNotContain(
                "photoPath", "purchasedOn", "size", "color", "material");
        assertThat(inventoryProperties.path("photoPath").path("type").toString()).contains("\"null\"");
        assertThat(inventoryProperties.path("purchasedOn").path("type").toString()).contains("\"null\"");
        assertThat(inventoryProperties.path("size").path("type").toString()).contains("\"null\"");
        assertThat(inventoryProperties.path("color").path("type").toString()).contains("\"null\"");
        assertThat(inventoryProperties.path("material").path("type").toString()).contains("\"null\"");
        assertThat(inventoryCreateProperties.has("imageUrl")).isFalse();
        assertThat(inventoryCreateProperties.has("photoUrl")).isFalse();
        assertThat(inventoryUpdateProperties.has("imageUrl")).isFalse();
        assertThat(inventoryUpdateProperties.has("photoUrl")).isFalse();
        assertThat(openApi.path("paths").has("/api/users/me/inventory/photos")).isFalse();
        assertThat(inventoryProperties.path("commerceReference").path("$ref").asText())
                .endsWith("/UserInventoryCommerceReferenceResponse");
        assertThat(commerceReferenceProperties.has("provider")).isTrue();
        assertThat(commerceReferenceProperties.has("externalMerchantId")).isTrue();
        assertThat(commerceReferenceProperties.has("sourceIdentity")).isTrue();
        assertThat(commerceReferenceProperties.has("externalProductId")).isTrue();
        assertThat(commerceReferenceProperties.path("selectedOptions").path("items").path("$ref").asText())
                .endsWith("/UserInventorySelectedOptionResponse");
        assertThat(selectedOptionProperties.has("group")).isTrue();
        assertThat(selectedOptionProperties.has("name")).isTrue();
        assertThat(selectedOptionProperties.has("value")).isTrue();
        assertThat(openedOperation.isMissingNode()).isFalse();
        assertThat(openedOperation.has("requestBody")).isFalse();
        assertThat(openedOperation.path("responses").has("204")).isTrue();
        assertThat(embeddedProperties.toString()).doesNotContain("clientSecret", "accessToken");
    }

    @Test
    void openedEndpointRequiresAuthenticationAndAcknowledgesRepeatedBodylessRequests() {
        UUID userId = UUID.randomUUID();
        CartResponse created = createCart(userId, saveMerchant().getId());
        CheckoutResponse checkout = checkoutCart(userId, created.cartId());
        Cart cart = embeddedReadyCart(created.cartId());
        EmbeddedCheckoutSessionBinding session = createEmbeddedSession(cart, EMBEDDED_ORIGIN);

        client.post().uri(
                        "/api/carts/{cartId}/checkout/embedded/{sessionId}/opened",
                        created.cartId(), session.sessionId())
                .header("Origin", EMBEDDED_ORIGIN)
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(checkoutPurchaseAttributionRepository
                .existsByUserIdAndCheckoutAttemptId(userId, checkout.checkoutAttemptId())).isFalse();

        client.post().uri(
                        "/api/carts/{cartId}/checkout/embedded/{sessionId}/opened",
                        created.cartId(), session.sessionId())
                .headers(headers -> headers.setBearerAuth(token(userId)))
                .exchange()
                .expectStatus().isBadRequest();

        for (int request = 0; request < 2; request++) {
            client.post().uri(
                            "/api/carts/{cartId}/checkout/embedded/{sessionId}/opened",
                            created.cartId(), session.sessionId())
                    .headers(headers -> headers.setBearerAuth(token(userId)))
                    .header("Origin", EMBEDDED_ORIGIN)
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectBody().isEmpty();
        }

        EmbeddedCheckoutSession persistedSession = embeddedCheckoutSessionRepository
                .findById(session.sessionId())
                .orElseThrow();
        assertThat(persistedSession.getOpenedAt()).isNotNull();

        var attributions = checkoutPurchaseAttributionRepository.findAll().stream()
                .filter(attribution -> attribution.getUserId().equals(userId))
                .filter(attribution -> attribution.getCheckoutAttemptId().equals(checkout.checkoutAttemptId()))
                .toList();
        assertThat(attributions).hasSize(1);
        assertThat(attributions.getFirst().getAttributionRail()).isEqualTo(CheckoutAttributionRail.EMBEDDED_CHECKOUT);
        assertThat(attributions.getFirst().getAttributionTrigger())
                .isEqualTo(CheckoutAttributionTrigger.CONFIRMED_ECP_START);
        assertThat(attributions.getFirst().getEmbeddedSessionId()).isEqualTo(session.sessionId());

        var inventoryItems = userInventoryItemRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        assertThat(inventoryItems).hasSize(1);
        assertThat(inventoryItems.getFirst().getSourceCheckoutAttemptId()).isEqualTo(checkout.checkoutAttemptId());
    }

    @Test
    void confirmedStartStillAttributesOnceWhenImmediateCloseWinsTheSessionRace() {
        UUID userId = UUID.randomUUID();
        CartResponse created = createCart(userId, saveMerchant().getId());
        CheckoutResponse checkout = checkoutCart(userId, created.cartId());
        Cart cart = embeddedReadyCart(created.cartId());
        EmbeddedCheckoutSessionBinding session = createEmbeddedSession(cart, EMBEDDED_ORIGIN);

        client.post().uri(
                        "/api/carts/{cartId}/checkout/embedded/{sessionId}/cancel",
                        created.cartId(), session.sessionId())
                .headers(headers -> headers.setBearerAuth(token(userId)))
                .header("Origin", EMBEDDED_ORIGIN)
                .exchange()
                .expectStatus().isNoContent();

        for (int request = 0; request < 2; request++) {
            client.post().uri(
                            "/api/carts/{cartId}/checkout/embedded/{sessionId}/opened",
                            created.cartId(), session.sessionId())
                    .headers(headers -> headers.setBearerAuth(token(userId)))
                    .header("Origin", EMBEDDED_ORIGIN)
                    .exchange()
                    .expectStatus().isNoContent();
        }

        EmbeddedCheckoutSession persistedSession = embeddedCheckoutSessionRepository
                .findById(session.sessionId())
                .orElseThrow();
        assertThat(persistedSession.getStatus()).isEqualTo(EmbeddedCheckoutSessionStatus.CANCELLED);
        assertThat(persistedSession.getOpenedAt()).isNotNull();
        assertThat(checkoutPurchaseAttributionRepository.findAll().stream()
                .filter(attribution -> attribution.getUserId().equals(userId))
                .filter(attribution -> attribution.getCheckoutAttemptId().equals(checkout.checkoutAttemptId())))
                .hasSize(1);
        assertThat(userInventoryItemRepository.findByUserIdOrderByUpdatedAtDesc(userId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getQuantity()).isEqualTo(1);
                    assertThat(item.getSourceCheckoutAttemptId()).isEqualTo(checkout.checkoutAttemptId());
                });
    }

    @Test
    void openedEndpointEnforcesOwnerOriginSessionAndFreshnessBindings() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Merchant merchant = saveMerchant();
        CartResponse firstCartResponse = createCart(ownerId, merchant.getId());
        checkoutCart(ownerId, firstCartResponse.cartId());
        Cart firstCart = embeddedReadyCart(firstCartResponse.cartId());
        EmbeddedCheckoutSessionBinding session = createEmbeddedSession(firstCart, EMBEDDED_ORIGIN);

        client.post().uri(
                        "/api/carts/{cartId}/checkout/embedded/{sessionId}/opened",
                        firstCart.getId(), session.sessionId())
                .headers(headers -> headers.setBearerAuth(token(otherUserId)))
                .header("Origin", EMBEDDED_ORIGIN)
                .exchange()
                .expectStatus().isNotFound();

        client.post().uri(
                        "/api/carts/{cartId}/checkout/embedded/{sessionId}/opened",
                        firstCart.getId(), session.sessionId())
                .headers(headers -> headers.setBearerAuth(token(ownerId)))
                .header("Origin", OTHER_ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isForbidden();

        CartResponse secondCartResponse = createCart(ownerId, merchant.getId());
        checkoutCart(ownerId, secondCartResponse.cartId());
        embeddedReadyCart(secondCartResponse.cartId());
        client.post().uri(
                        "/api/carts/{cartId}/checkout/embedded/{sessionId}/opened",
                        secondCartResponse.cartId(), session.sessionId())
                .headers(headers -> headers.setBearerAuth(token(ownerId)))
                .header("Origin", EMBEDDED_ORIGIN)
                .exchange()
                .expectStatus().isForbidden();

        EmbeddedCheckoutSessionBinding staleAttemptSession = embeddedCheckoutSessionStore.create(
                new CreateEmbeddedCheckoutSessionCommand(
                        ownerId,
                        firstCart.getId(),
                        firstCart.getCheckoutId(),
                        UUID.randomUUID(),
                        firstCart.getMerchantIntegrationId(),
                        firstCart.getRoutingScopeKey(),
                        EMBEDDED_ORIGIN,
                        "2026-04-08"
                ));
        client.post().uri(
                        "/api/carts/{cartId}/checkout/embedded/{sessionId}/opened",
                        firstCart.getId(), staleAttemptSession.sessionId())
                .headers(headers -> headers.setBearerAuth(token(ownerId)))
                .header("Origin", EMBEDDED_ORIGIN)
                .exchange()
                .expectStatus().isForbidden();

        EmbeddedCheckoutSession expiredSession = embeddedCheckoutSessionRepository.saveAndFlush(
                EmbeddedCheckoutSession.builder()
                        .userId(ownerId)
                        .cartId(firstCart.getId())
                        .checkoutId(firstCart.getCheckoutId())
                        .checkoutAttemptId(firstCart.getCheckoutAttemptId())
                        .merchantIntegrationId(firstCart.getMerchantIntegrationId())
                        .routingScopeKey(firstCart.getRoutingScopeKey())
                        .allowedOrigin(EMBEDDED_ORIGIN)
                        .protocolVersion("2026-04-08")
                        .status(EmbeddedCheckoutSessionStatus.ACTIVE)
                        .expiresAt(Instant.now().minusSeconds(1))
                        .createdAt(Instant.now().minusSeconds(2))
                        .build());
        client.post().uri(
                        "/api/carts/{cartId}/checkout/embedded/{sessionId}/opened",
                        firstCart.getId(), expiredSession.getId())
                .headers(headers -> headers.setBearerAuth(token(ownerId)))
                .header("Origin", EMBEDDED_ORIGIN)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void nullAddItemIsRejectedAtTheHttpBoundary() {
        client.post().uri("/api/carts")
                .headers(headers -> {
                    headers.setBearerAuth(token(UUID.randomUUID()));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "addItems": [null],
                          "discountCodes": [],
                          "giftCardCodes": []
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest();

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
                              "offerKey": "%s|gid://shopify/ProductVariant/2",
                              "quantity": 1
                            }
                          ]
                        }
                        """.formatted(merchant.getId()))
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
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(CheckoutResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(checkout).isNotNull();
        assertThat(checkout.cartId()).isEqualTo(created.cartId());
        assertThat(checkout.checkoutUrl()).contains("checkout");
        assertThat(checkout.continueUrl()).contains("continue");
        assertThat(checkout.nextAction()).isEqualTo(CheckoutNextAction.UNKNOWN);
        assertThat(checkoutDispatchService.createCount()).isEqualTo(1);
        assertThat(checkoutDispatchService.lastCallContext()).isNotNull();
        assertThat(checkoutDispatchService.lastCallContext().buyerIp()).isNotBlank();

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
    void ownerCheckoutDetailsPersistAndAreOfferedOnlyToThatOwnerOnLaterCarts() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Merchant merchant = saveMerchant();
        CartResponse firstCart = createCart(ownerId, merchant.getId());
        checkoutCart(ownerId, firstCart.cartId());

        CheckoutResponse updated = client.patch()
                .uri("/api/carts/{cartId}/checkout", firstCart.cartId())
                .headers(headers -> {
                    headers.setBearerAuth(token(ownerId));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "buyer": {
                            "email": "ada@example.com",
                            "firstName": "Ada",
                            "lastName": "Lovelace",
                            "phoneNumber": "+44 20 7946 0958"
                          },
                          "shippingAddress": {
                            "streetAddress": "12 St James Square",
                            "extendedAddress": "Flat 3",
                            "addressLocality": "London",
                            "addressRegion": "Greater London",
                            "postalCode": "SW1Y 4LB",
                            "addressCountry": "GB"
                          }
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(CheckoutResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.savedCheckoutDetails()).isNotNull();
        assertThat(updated.savedCheckoutDetails().buyer().email()).isEqualTo("ada@example.com");
        assertThat(updated.savedCheckoutDetails().shippingAddress().postalCode()).isEqualTo("SW1Y 4LB");

        CartResponse laterCart = createCart(ownerId, merchant.getId());
        CheckoutResponse laterCheckout = checkoutCart(ownerId, laterCart.cartId());
        assertThat(laterCheckout.savedCheckoutDetails()).isEqualTo(updated.savedCheckoutDetails());

        CartResponse otherUsersCart = createCart(otherUserId, merchant.getId());
        CheckoutResponse otherUsersCheckout = checkoutCart(otherUserId, otherUsersCart.cartId());
        assertThat(otherUsersCheckout.savedCheckoutDetails()).isNull();
    }

    @Test
    void clientSuppliedRoutingCommercialAndVariantFieldsAreNonAuthoritative() {
        UUID userId = UUID.randomUUID();
        Merchant merchant = saveMerchant();
        String selectedVariant = "gid://shopify/ProductVariant/server-selected";

        client.post().uri("/api/carts")
                .headers(headers -> {
                    headers.setBearerAuth(token(userId));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "merchantId": "%s",
                          "endpoint": "https://attacker.example/mcp",
                          "price": "0.01",
                          "currency": "XXX",
                          "addItems": [{
                            "offerKey": "%s|%s",
                            "productVariantId": "attacker-variant",
                            "selectedOptions": [{"name":"Color","value":"Tampered"}],
                            "quantity": 1
                          }]
                        }
                        """.formatted(UUID.randomUUID(), merchant.getId(), selectedVariant))
                .exchange()
                .expectStatus().isOk();

        assertThat(cartDispatchService.lastCreatedVariant()).isEqualTo(selectedVariant);
        assertThat(cartDispatchService.lastCallContext().buyerIp()).isNotBlank();
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
                              "offerKey": "%s|gid://shopify/ProductVariant/2",
                              "quantity": 1
                            }
                          ]
                        }
                        """.formatted(merchant.getId()))
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

    private CheckoutResponse checkoutCart(UUID userId, UUID cartId) {
        CheckoutResponse checkout = client.get().uri("/api/carts/{cartId}/checkout", cartId)
                .headers(headers -> headers.setBearerAuth(token(userId)))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(CheckoutResponse.class)
                .returnResult()
                .getResponseBody();
        assertThat(checkout).isNotNull();
        assertThat(checkout.checkoutId()).isNotBlank();
        assertThat(checkout.checkoutAttemptId()).isNotNull();
        return checkout;
    }

    private EmbeddedCheckoutSessionBinding createEmbeddedSession(Cart cart, String origin) {
        return embeddedCheckoutSessionStore.create(new CreateEmbeddedCheckoutSessionCommand(
                cart.getUserId(),
                cart.getId(),
                cart.getCheckoutId(),
                cart.getCheckoutAttemptId(),
                cart.getMerchantIntegrationId(),
                cart.getRoutingScopeKey(),
                origin,
                "2026-04-08"
        ));
    }

    private Cart embeddedReadyCart(UUID cartId) {
        Cart cart = cartRepository.findById(cartId).orElseThrow();
        if (cart.getRoutingScopeKey() == null || cart.getRoutingScopeKey().isBlank()) {
            String merchantIdentity = cart.getMerchantId().toString();
            cart.replaceCheckoutSession(
                    cart.getCheckoutId(),
                    cart.getCheckoutStatus(),
                    cart.getCheckoutUrl(),
                    cart.getContinueUrl(),
                    null,
                    cart.getCheckoutProtocolVersion(),
                    cart.getCheckoutLifecycleState(),
                    cart.getCheckoutSynchronizedAt()
            );
            cart.assignRoutingScope(
                    cart.getProvider() == null ? MerchantIntegrationProvider.GENERIC_UCP.name() : cart.getProvider(),
                    null,
                    merchantIdentity,
                    "EXTERNAL:merchant:" + merchantIdentity,
                    null,
                    cart.getMerchantDomain()
            );
            return cartRepository.saveAndFlush(cart);
        }
        return cart;
    }

    private String createCartBody(UUID merchantId) {
        return """
                {
                  "addItems": [
                    {
                      "offerKey": "%s|gid://shopify/ProductVariant/1",
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

        @Bean
        @Primary
        FakeSelectedOfferResolutionService testSelectedOfferResolutionService() {
            return new FakeSelectedOfferResolutionService();
        }

        @Bean
        @Primary
        FakeSelectedOfferCartRoutingService testSelectedOfferCartRoutingService(
                MerchantCartProviderLookupService providerLookupService,
                CartBindingMetrics metrics
        ) {
            return new FakeSelectedOfferCartRoutingService(providerLookupService, metrics);
        }

        @Bean
        @Primary
        FakeCartOfferRevalidationService testCartOfferRevalidationService(CartBindingMetrics metrics) {
            return new FakeCartOfferRevalidationService(metrics);
        }
    }

    static class FakeCartDispatchService extends MerchantCartPluginDispatchService {

        private final AtomicInteger createCount = new AtomicInteger();
        private final AtomicInteger updateCount = new AtomicInteger();
        private final AtomicInteger getCount = new AtomicInteger();
        private final AtomicInteger cancelCount = new AtomicInteger();
        private final AtomicInteger cartSequence = new AtomicInteger();
        private final AtomicReference<String> lastCreatedVariant = new AtomicReference<>();
        private final AtomicReference<CartToolCallContext> lastCallContext = new AtomicReference<>();

        FakeCartDispatchService() {
            super(org.mockito.Mockito.mock(com.meant.api.module.merchant.service.MerchantMcpToolClient.class),
                    org.mockito.Mockito.mock(com.meant.api.plugin.transport.registry.CapabilityRegistry.class),
                    new tools.jackson.databind.ObjectMapper(), List.of(),
                    new CartBindingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        }

        void reset() {
            createCount.set(0);
            updateCount.set(0);
            getCount.set(0);
            cancelCount.set(0);
            lastCreatedVariant.set(null);
            lastCallContext.set(null);
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

        String lastCreatedVariant() {
            return lastCreatedVariant.get();
        }

        CartToolCallContext lastCallContext() {
            return lastCallContext.get();
        }

        @Override
        public UcpCartToolResult createCart(
                CartRoutingTarget target,
                CreateCartRequest request,
                UcpSession session
        ) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            createCount.incrementAndGet();
            lastCreatedVariant.set(request.addItems().getFirst().productVariantId());
            return cartToolResult(request.addItems().getFirst().productVariantId());
        }

        @Override
        public UcpCartToolResult createCart(
                CartRoutingTarget target,
                CreateCartRequest request,
                UcpSession session,
                CartToolCallContext callContext
        ) {
            lastCallContext.set(callContext);
            return createCart(target, request, session);
        }

        @Override
        public UcpCartToolResult updateCart(
                CartRoutingTarget target,
                UpdateCartRequest request,
                UcpSession session
        ) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            updateCount.incrementAndGet();
            return cartToolResult(request.addItems().isEmpty()
                    ? "gid://shopify/ProductVariant/1"
                    : request.addItems().getFirst().productVariantId());
        }

        @Override
        public UcpCartToolResult updateCart(
                CartRoutingTarget target,
                UpdateCartRequest request,
                UcpSession session,
                CartToolCallContext callContext
        ) {
            lastCallContext.set(callContext);
            return updateCart(target, request, session);
        }

        @Override
        public CancelCartResponse cancelCart(
                CartRoutingTarget target,
                CancelCartRequest request,
                UcpSession session,
                UUID idempotencyKey
        ) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            cancelCount.incrementAndGet();
            return new CancelCartResponse(request.cartId(), "canceled", true, List.of(), List.of());
        }

        @Override
        public CancelCartResponse cancelCart(
                CartRoutingTarget target,
                CancelCartRequest request,
                UcpSession session,
                CartToolCallContext callContext
        ) {
            lastCallContext.set(callContext);
            return cancelCart(target, request, session, callContext.idempotencyKey());
        }

        @Override
        public UcpCartToolResult createCart(
                MerchantCartProvider provider,
                CreateCartRequest request,
                UcpSession session
        ) {
            createCount.incrementAndGet();
            lastCreatedVariant.set(request.addItems().getFirst().productVariantId());
            return cartToolResult(request.addItems().getFirst().productVariantId());
        }

        @Override
        public UcpCartToolResult updateCart(
                MerchantCartProvider provider,
                UpdateCartRequest request,
                UcpSession session
        ) {
            updateCount.incrementAndGet();
            return cartToolResult(request.addItems().isEmpty()
                    ? "gid://shopify/ProductVariant/1"
                    : request.addItems().getFirst().productVariantId());
        }

        @Override
        public UcpCartToolResult getCart(
                CartRoutingTarget target,
                GetCartRequest request,
                UcpSession session,
                CartToolCallContext callContext
        ) {
            lastCallContext.set(callContext);
            return getCart(target.merchantProvider(), request, session);
        }

        @Override
        public UcpCartToolResult getCart(
                MerchantCartProvider provider,
                GetCartRequest request,
                UcpSession session
        ) {
            getCount.incrementAndGet();
            return cartToolResult("gid://shopify/ProductVariant/1");
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

        private UcpCartToolResult cartToolResult(String variantId) {
            int sequence = cartSequence.incrementAndGet();
            UcpCartResponse response = new UcpCartResponse(
                    "Checkout when ready",
                    new UcpCartResponse.Cart(
                            "gid://shopify/Cart/" + sequence,
                            Instant.parse("2026-06-16T11:05:00Z"),
                            Instant.parse("2026-06-16T11:05:01Z"),
                            null,
                            List.of(cartLine(sequence, variantId)),
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

        private UcpCartResponse.Line cartLine(int sequence, String variantId) {
            return new UcpCartResponse.Line(
                    "gid://shopify/CartLine/" + sequence,
                    1,
                    new UcpCartResponse.Cost(
                            new UcpCartResponse.Money("14.95", "USD"),
                            new UcpCartResponse.Money("14.95", "USD")
                    ),
                    new UcpCartResponse.Merchandise(
                            variantId,
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

    static class FakeSelectedOfferResolutionService extends UserSelectedOfferResolutionService {
        FakeSelectedOfferResolutionService() {
            super(null, null, null, null, null);
        }

        @Override
        public ResolvedSelectedOffer resolve(ResolveUserSelectedOfferQuery query) {
            String[] parts = query.offerKey().split("\\|", 2);
            UUID merchantId = UUID.fromString(parts[0]);
            String variantId = parts[1];
            ProviderIdentity provider = new ProviderIdentity("GENERIC_UCP");
            ExternalIdentifier product = new ExternalIdentifier(
                    ExternalIdentifierType.PRODUCT, provider.value(), "product:" + variantId);
            ExternalIdentifier variant = new ExternalIdentifier(
                    ExternalIdentifierType.VARIANT, provider.value(), variantId);
            LocalMerchantRouting routing = new LocalMerchantRouting(merchantId);
            DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                    provider, ResultSourceType.MERCHANT_STOREFRONT, merchantId.toString());
            OfferIdentity identity = new OfferIdentity(
                    provider, OfferMerchantScope.localIntegrationFallback(merchantId), product, variant,
                    List.of(), List.of(), null);
            ResultProvenance provenance = new ResultProvenance(
                    provider, source, routing, null, product, variant, new ResultFreshness(Instant.now(), null),
                    new ResultSourceReference(ResultSourceType.MERCHANT_STOREFRONT, "test", null));
            CatalogProductReference reference = new CatalogProductReference(
                    query.offerKey(), source, null, routing, null, product, variant, List.of());
            return new ResolvedSelectedOffer(merchantId.toString(), query.offerKey(), identity, provenance, reference);
        }

        @Override
        public List<ResolvedSelectedOffer> resolveAll(ResolveUserSelectedOffersQuery query) {
            return query.offerKeys().stream()
                    .map(offerKey -> resolve(new ResolveUserSelectedOfferQuery(
                            query.userId(), offerKey, query.countryCode())))
                    .toList();
        }
    }

    static class FakeSelectedOfferCartRoutingService extends SelectedOfferCartRoutingService {
        private final MerchantCartProviderLookupService providerLookupService;

        FakeSelectedOfferCartRoutingService(
                MerchantCartProviderLookupService providerLookupService,
                CartBindingMetrics metrics
        ) {
            super(null, null, List.of(), metrics);
            this.providerLookupService = providerLookupService;
        }

        @Override
        public CartRoutingTarget resolve(ResolvedSelectedOffer offer) {
            UUID merchantId = UUID.fromString(offer.canonicalProductKey());
            MerchantCartProvider provider = providerLookupService.findById(merchantId).orElseThrow();
            return new CartRoutingTarget(
                    "LEGACY:merchant:" + merchantId,
                    MerchantIntegrationProvider.GENERIC_UCP,
                    null,
                    null,
                    provider);
        }
    }

    static class FakeCartOfferRevalidationService extends CartOfferRevalidationService {
        FakeCartOfferRevalidationService(CartBindingMetrics metrics) {
            super(null, null, metrics);
        }

        @Override
        public void revalidate(Cart cart, String countryCode) {
            // Controller integration tests exercise ownership and request flow, not provider rehydration.
        }
    }

    static class FakeCheckoutDispatchService extends MerchantCheckoutPluginDispatchService {

        private final AtomicInteger createCount = new AtomicInteger();
        private final AtomicReference<CheckoutToolCallContext> lastCallContext = new AtomicReference<>();
        private final Map<String, String> checkoutCartIds = new ConcurrentHashMap<>();

        FakeCheckoutDispatchService() {
            super(org.mockito.Mockito.mock(com.meant.api.module.merchant.service.MerchantMcpToolClient.class),
                    org.mockito.Mockito.mock(com.meant.api.plugin.transport.registry.CapabilityRegistry.class),
                    new tools.jackson.databind.ObjectMapper(), java.util.List.of());
        }

        void reset() {
            createCount.set(0);
            lastCallContext.set(null);
            checkoutCartIds.clear();
        }

        int createCount() {
            return createCount.get();
        }

        CheckoutToolCallContext lastCallContext() {
            return lastCallContext.get();
        }

        @Override
        public UcpCheckoutToolResult createCheckout(
                MerchantCartProvider provider,
                CreateCheckoutRequest request,
                UcpSession session
        ) {
            createCount.incrementAndGet();
            String checkoutId = "gid://shopify/Checkout/" + createCount.get();
            checkoutCartIds.put(checkoutId, request.cartId());
            UcpCheckoutResponse response = new UcpCheckoutResponse(
                    null,
                    "Open checkout in browser",
                    new UcpCheckoutResponse.Checkout(
                            checkoutId,
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

        @Override
        public UcpCheckoutToolResult createCheckout(
                CartRoutingTarget target,
                CreateCheckoutRequest request,
                UcpSession session,
                CheckoutToolCallContext context
        ) {
            lastCallContext.set(context);
            return createCheckout(target.merchantProvider(), request, session);
        }

        @Override
        public UcpCheckoutToolResult getCheckout(
                CartRoutingTarget target,
                GetCheckoutRequest request,
                UcpSession session,
                CheckoutToolCallContext context
        ) {
            lastCallContext.set(context);
            return checkoutToolResult(request.checkoutId());
        }

        @Override
        public UcpCheckoutToolResult updateCheckout(
                CartRoutingTarget target,
                UpdateCheckoutRequest request,
                UcpSession session,
                CheckoutToolCallContext context
        ) {
            lastCallContext.set(context);
            return checkoutToolResult(request.checkoutId());
        }

        private UcpCheckoutToolResult checkoutToolResult(String checkoutId) {
            String payload = """
                    {
                      "checkout": {
                        "id": "%s",
                        "cart_id": "%s",
                        "status": "incomplete",
                        "checkout_url": "https://merchant.example/checkout/current",
                        "continue_url": "https://merchant.example/continue/current",
                        "line_items": [{
                          "id": "gid://shopify/CheckoutLine/1",
                          "product_variant_id": "gid://shopify/ProductVariant/1",
                          "quantity": 1
                        }]
                      }
                    }
                    """.formatted(checkoutId, checkoutCartIds.get(checkoutId));
            try {
                UcpCheckoutResponse response = new ObjectMapper().readValue(payload, UcpCheckoutResponse.class);
                return new UcpCheckoutToolResult(
                        "https://merchant.example/api/mcp",
                        payload,
                        response
                );
            } catch (JacksonException exception) {
                throw new AssertionError(exception);
            }
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
