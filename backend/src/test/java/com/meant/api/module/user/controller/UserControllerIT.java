package com.meant.api.module.user.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.PostgresIntegrationTest;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.controller.response.UserAssistantConversationSummaryResponse;
import com.meant.api.module.user.controller.response.UserInventoryExportResponse;
import com.meant.api.module.user.controller.response.UserInventoryItemResponse;
import com.meant.api.module.user.controller.response.UserPopularProductSearchResponse;
import com.meant.api.module.user.controller.response.UserProductDiscoveryResponse;
import com.meant.api.module.user.controller.response.UserResponse;
import com.meant.api.module.user.controller.response.UserSavedProductResponse;
import com.meant.api.module.user.controller.response.UserSettingsResponse;
import com.meant.api.module.user.entity.UserAssistantConversation;
import com.meant.api.module.user.entity.UserProductRecommendationExplanation;
import com.meant.api.module.user.entity.UserProductSearch;
import com.meant.api.module.user.entity.UserProductSearchEvent;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.repository.UserProductRecommendationExplanationRepository;
import com.meant.api.module.user.repository.UserProductSearchEventRepository;
import com.meant.api.module.user.repository.UserProductSearchRepository;
import com.meant.api.module.user.repository.UserAssistantConversationRepository;
import com.meant.api.module.user.repository.UserProductSearchResultItemRepository;
import com.meant.api.module.user.repository.UserRepository;
import com.meant.api.module.user.service.UserProductSearchHashService;
import com.meant.api.module.user.service.UserSettingsService;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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
import org.springframework.web.client.RestClient;

/**
 * Boots the full app on a random port and drives it over HTTP. A test {@link JwtDecoder} turns
 * opaque bearer tokens of the form {@code <userId>|<email>|<fullName>} into a {@link Jwt}, so we can
 * exercise the resource-server filter chain without standing up Supabase or signing real tokens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerIT extends PostgresIntegrationTest {

    /** Mirrors {@code UserController.MAX_CONVERSATION_LIMIT}; kept local to avoid exposing the constant. */
    private static final int MAX_CONVERSATION_LIMIT_FIXTURE = 50;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserProductSearchHashService userProductSearchHashService;

    @Autowired
    private UserProductSearchRepository userProductSearchRepository;

    @Autowired
    private UserProductSearchResultItemRepository userProductSearchResultItemRepository;

    @Autowired
    private UserProductSearchEventRepository userProductSearchEventRepository;

    @Autowired
    private UserProductRecommendationExplanationRepository userProductRecommendationExplanationRepository;

    @Autowired
    private UserAssistantConversationRepository userAssistantConversationRepository;

    @Autowired
    private UserProductSearchProperties userProductSearchProperties;

    @Autowired
    private OpenRouterProperties openRouterProperties;

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

        @Bean
        @Primary
        OpenRouterChatClient testOpenRouterChatClient() {
            return new OpenRouterChatClient(
                    RestClient.builder(),
                    new OpenRouterProperties(
                            "https://openrouter.test/api/v1",
                            "test-key",
                            "Meant",
                            new OpenRouterProperties.Models(
                                    "preference-model",
                                    "openrouter/free",
                                    "explainer-model",
                                    "openrouter/free"
                            )
                    )
            ) {
                @Override
                public String completeJson(
                        String model,
                        String systemPrompt,
                        String userPrompt,
                        String schemaName,
                        OpenRouterJsonSchemaDefinition schema
                ) {
                    return """
                            {
                              "searches": [
                                {
                                  "displayQuery": "Polished cotton tees under $50",
                                  "query": "Polished cotton tees under $50"
                                }
                              ]
                            }
                            """;
                }
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
    void patchUpdatesProfilePicturePath() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        String profilePicturePath = id + "/avatar.webp";

        UserResponse body = client.patch().uri("/api/users/me/profile-picture")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, null));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("{\"profilePicturePath\":\"%s\"}".formatted(profilePicturePath))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.profilePicturePath()).isEqualTo(profilePicturePath);
        assertThat(userRepository.findById(id).orElseThrow().getProfilePicturePath()).isEqualTo(profilePicturePath);
    }

    @Test
    void patchRejectsProfilePicturePathOutsideUserFolder() {
        UUID id = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        String email = id + "@example.com";

        client.patch().uri("/api/users/me/profile-picture")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, null));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("{\"profilePicturePath\":\"%s/avatar.webp\"}".formatted(otherUserId))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void deleteRemovesProfilePicturePath() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        String profilePicturePath = id + "/avatar.webp";

        client.patch().uri("/api/users/me/profile-picture")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, null));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("{\"profilePicturePath\":\"%s\"}".formatted(profilePicturePath))
                .exchange()
                .expectStatus().isOk();

        UserResponse body = client.delete().uri("/api/users/me/profile-picture")
                .headers(headers -> headers.setBearerAuth(token(id, email, null)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.profilePicturePath()).isNull();
        assertThat(userRepository.findById(id).orElseThrow().getProfilePicturePath()).isNull();
    }

    @Test
    void settingsReturnsDefaultCanonicalFilters() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";

        UserSettingsResponse body = client.get().uri("/api/users/me/settings")
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserSettingsResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.budget()).isEqualTo(120);
        assertThat(body.availableFilters()).extracting("id")
                .contains("organic", "gluten-free", "no-polyester", "highly-rated", "crypto");
        assertThat(body.filters()).extracting("id")
                .containsExactly(
                        "organic",
                        "low-sugar",
                        "natural-materials",
                        "no-polyester",
                        "sustainable-brands",
                        "best-value",
                        "highly-rated");
    }

    @Test
    void patchSettingsUpdatesFiltersBudgetLocationAndClothingFit() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";

        UserSettingsResponse body = client.patch().uri("/api/users/me/settings")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, null));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "budget": 95,
                          "clothingFit": "men",
                          "locations": [
                            {
                              "country": "United States",
                              "code": "US",
                              "city": "New York"
                            },
                            {
                              "country": "Canada",
                              "code": "CA",
                              "city": "Toronto"
                            }
                          ],
                          "filterIds": ["organic", "gluten-free", "fast-shipping"]
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserSettingsResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.budget()).isEqualTo(95);
        assertThat(body.clothingFit()).isEqualTo("men");
        assertThat(body.location()).isNotNull();
        assertThat(body.location().code()).isEqualTo("US");
        assertThat(body.locations()).extracting("code").containsExactly("US", "CA");
        assertThat(body.filters()).extracting("id")
                .containsExactly("organic", "gluten-free", "fast-shipping");
    }

    @Test
    void patchSettingsClearsBudgetLocationsAndClothingFit() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";

        client.patch().uri("/api/users/me/settings")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, null));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "budget": 95,
                          "clothingFit": "women",
                          "locations": [
                            {
                              "country": "United States",
                              "code": "US",
                              "city": "New York"
                            }
                          ]
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        UserSettingsResponse body = client.patch().uri("/api/users/me/settings")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, null));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "budgetUnlimited": true,
                          "clothingFit": "none",
                          "locations": []
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserSettingsResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.budget()).isNull();
        assertThat(body.clothingFit()).isNull();
        assertThat(body.location()).isNull();
        assertThat(body.locations()).isEmpty();
    }

    @Test
    void savedProductsCanBeSavedListedAndRemoved() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        String productKey = "shop.example:gid://shopify/Product/123";

        UserSavedProductResponse saved = client.post().uri("/api/users/me/saved-products")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, "Ada Lovelace"));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "id": "shop.example:gid://shopify/Product/123",
                          "productHash": "hash-1",
                          "name": "Saved Cereal",
                          "brand": "Wholegrain Co.",
                          "category": "Groceries",
                          "tone": "#e9ede8",
                          "imageUrl": "https://example.com/cereal.png",
                          "productUrl": "https://shop.example/products/cereal",
                          "remote": true,
                          "match": 96,
                          "priceFrom": 7.4,
                          "merchants": 1,
                          "satisfies": ["organic", "low-sugar"],
                          "misses": [],
                          "note": "Organic and low sugar.",
                          "pros": ["No refined sugar"],
                          "cons": ["Pricier than own-brand"],
                          "review": {
                            "score": 4.8,
                            "count": 2140,
                            "insight": "Reviewers consistently repurchase."
                          },
                          "offers": [
                            {
                              "merchant": "Whole Foods",
                              "price": 7.4,
                              "delivery": "Tomorrow",
                              "merchantId": "merchant-1",
                              "merchantDomain": "shop.example",
                              "productVariantId": "variant-1",
                              "variantTitle": "Default",
                              "available": true
                            }
                          ],
                          "needs": null,
                          "provides": []
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserSavedProductResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(saved).isNotNull();
        assertThat(saved.id()).isEqualTo(productKey);
        assertThat(saved.offers()).singleElement()
                .extracting(UserSavedProductResponse.Offer::merchant)
                .isEqualTo("Whole Foods");

        UserSavedProductResponse[] listed = client.get().uri("/api/users/me/saved-products")
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserSavedProductResponse[].class)
                .returnResult()
                .getResponseBody();

        assertThat(listed).isNotNull();
        assertThat(listed).singleElement()
                .extracting(UserSavedProductResponse::id)
                .isEqualTo(productKey);

        UserProductDiscoveryResponse discovery = client.get().uri("/api/users/me/product-discovery")
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserProductDiscoveryResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(discovery).isNotNull();
        assertThat(discovery.savedProducts()).singleElement()
                .extracting(UserSavedProductResponse::id)
                .isEqualTo(productKey);
        assertThat(discovery.recentProducts()).isEmpty();

        client.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/users/me/saved-products")
                        .queryParam("productKey", productKey)
                        .build())
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isNoContent();

        UserSavedProductResponse[] afterDelete = client.get().uri("/api/users/me/saved-products")
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserSavedProductResponse[].class)
                .returnResult()
                .getResponseBody();

        assertThat(afterDelete).isEmpty();
    }

    @Test
    void inventoryItemsCanBeAddedFromManualEntryAndPhotoListedExportedUpdatedAndDeleted() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        String bearer = token(id, email, "Ada Lovelace");

        UserInventoryItemResponse manual = client.post().uri("/api/users/me/inventory")
                .headers(headers -> {
                    headers.setBearerAuth(bearer);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "name": "Cold-Pressed Extra Virgin Olive Oil",
                          "brand": "Casa Verde",
                          "category": "PANTRY",
                          "quantity": 1,
                          "unit": "bottle",
                          "location": "Pantry",
                          "attributes": ["organic", "single-estate"],
                          "consumable": true,
                          "restockEnabled": true,
                          "restockThreshold": 1
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserInventoryItemResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(manual).isNotNull();
        assertThat(manual.name()).isEqualTo("Cold-Pressed Extra Virgin Olive Oil");
        assertThat(manual.restockEnabled()).isTrue();

        UserInventoryItemResponse photo = client.post().uri("/api/users/me/inventory/photos")
                .headers(headers -> {
                    headers.setBearerAuth(bearer);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "photoUrl": "data:image/jpeg;base64,abc",
                          "name": "Blue Linen Shirt",
                          "category": "APPAREL",
                          "quantity": 1,
                          "location": "Closet"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserInventoryItemResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(photo).isNotNull();
        assertThat(photo.name()).isEqualTo("Blue Linen Shirt");
        assertThat(photo.photoUrl()).isEqualTo("data:image/jpeg;base64,abc");

        UserInventoryItemResponse[] restocks = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/users/me/inventory")
                        .queryParam("restockOnly", true)
                        .build())
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserInventoryItemResponse[].class)
                .returnResult()
                .getResponseBody();

        assertThat(restocks).isNotNull();
        assertThat(restocks).singleElement()
                .extracting(UserInventoryItemResponse::id)
                .isEqualTo(manual.id());

        UserInventoryItemResponse updated = client.patch().uri("/api/users/me/inventory/{itemId}", manual.id())
                .headers(headers -> {
                    headers.setBearerAuth(bearer);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "quantity": 2,
                          "restockEnabled": false
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserInventoryItemResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated).isNotNull();
        assertThat(updated.quantity()).isEqualTo(2);
        assertThat(updated.restockEnabled()).isFalse();

        UserInventoryExportResponse export = client.get().uri("/api/users/me/inventory/export")
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserInventoryExportResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(export).isNotNull();
        assertThat(export.items()).hasSize(2);

        client.delete().uri("/api/users/me/inventory/{itemId}", photo.id())
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus().isNoContent();

        UserInventoryItemResponse[] listed = client.get().uri("/api/users/me/inventory")
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserInventoryItemResponse[].class)
                .returnResult()
                .getResponseBody();

        assertThat(listed).isNotNull();
        assertThat(listed).singleElement()
                .extracting(UserInventoryItemResponse::id)
                .isEqualTo(manual.id());
    }

    @Test
    void productDiscoveryReturnsRecentSearchProductsFromUserCache() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        UpsertUserCommand upsertCommand = new UpsertUserCommand(id, email, "Ada", "Lovelace");
        UserSettingsResult settings = userSettingsService.get(upsertCommand);
        String profileHash = userProductSearchHashService.profileHash(settings);
        Instant now = Instant.now();
        String productKey = "merchant.example:tee";
        String productHash = "hash-tee";

        UserProductSearch search = userProductSearchRepository.save(UserProductSearch.create(
                id,
                "cotton tee",
                "cotton tee",
                profileHash,
                userProductSearchProperties.searchVersion(),
                now,
                now.plusSeconds(3600),
                false
        ));
        userProductSearchResultItemRepository.save(UserProductSearchResultItem.from(
                search.getId(),
                productKey,
                productHash,
                recentSearchProduct(),
                now
        ));
        userProductRecommendationExplanationRepository.save(UserProductRecommendationExplanation.create(
                id,
                "cotton tee",
                profileHash,
                productKey,
                productHash,
                openRouterProperties.models().productRecommendationExplainer(),
                userProductSearchProperties.explanationPromptVersion(),
                "Organic cotton and no polyester match your profile.",
                now
        ));

        UserProductDiscoveryResponse discovery = client.get().uri("/api/users/me/product-discovery")
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserProductDiscoveryResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(discovery).isNotNull();
        assertThat(discovery.savedProducts()).isEmpty();
        assertThat(discovery.recentProducts()).singleElement()
                .satisfies(product -> {
                    assertThat(product.productKey()).isEqualTo(productKey);
                    assertThat(product.title()).isEqualTo("Organic Cotton Tee");
                    assertThat(product.whyMeantForYou()).isEqualTo(
                            "Organic cotton and no polyester match your profile.");
                });
    }

    @Test
    void popularProductSearchesReturnsDistinctUserAggregates() {
        Instant now = Instant.now();
        String displayQuery = "Organic cotton T-shirt under $50";
        for (int index = 0; index < 3; index++) {
            UUID userId = UUID.randomUUID();
            String email = userId + "@example.com";
            userSettingsService.get(new UpsertUserCommand(userId, email, "User", String.valueOf(index)));
            userProductSearchEventRepository.save(UserProductSearchEvent.from(
                    userId,
                    null,
                    openRouterProperties.models().productSearchQueryParser(),
                    userProductSearchProperties.queryParserPromptVersion(),
                    queryIntent("organic cotton t-shirt under $50", displayQuery),
                    4,
                    now.minusSeconds(index)
            ));
        }
        UUID scopedUserId = UUID.randomUUID();
        userSettingsService.get(new UpsertUserCommand(scopedUserId, scopedUserId + "@example.com", "Scoped", "User"));
        userProductSearchEventRepository.save(UserProductSearchEvent.from(
                scopedUserId,
                UUID.randomUUID(),
                openRouterProperties.models().productSearchQueryParser(),
                userProductSearchProperties.queryParserPromptVersion(),
                queryIntent("organic cotton t-shirt under $50", displayQuery),
                4,
                now
        ));

        UserPopularProductSearchResponse[] popular = client.get().uri("/api/users/me/popular-product-searches")
                .headers(headers -> headers.setBearerAuth(token(UUID.randomUUID(), "viewer@example.com", "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserPopularProductSearchResponse[].class)
                .returnResult()
                .getResponseBody();

        assertThat(popular).isNotNull();
        assertThat(popular).singleElement()
                .satisfies(search -> {
                    assertThat(search.displayQuery()).isEqualTo("Polished cotton tees under $50");
                    assertThat(search.query()).isEqualTo("Polished cotton tees under $50");
                });
    }

    @Test
    void assistantConversationsClampsOversizedAndNonPositiveLimit() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";

        // Seed more conversations than the server-side cap so an oversized page request can be observed
        // to return a bounded slice rather than every row (MEA-27 — OWASP API4 Unrestricted Resource
        // Consumption). The endpoint upserts the user on read, so no users row is required up front.
        Instant now = Instant.now();
        int seeded = MAX_CONVERSATION_LIMIT_FIXTURE + 5;
        for (int i = 0; i < seeded; i++) {
            userAssistantConversationRepository.save(
                    UserAssistantConversation.create(id, "Conversation " + i, now.plusSeconds(i)));
        }

        // An unbounded page size must be clamped to MAX_CONVERSATION_LIMIT_FIXTURE, not pull excessive rows.
        var clamped = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/users/me/assistant/conversations")
                        .queryParam("limit", 1_000_000)
                        .build())
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserAssistantConversationSummaryResponse[].class)
                .returnResult()
                .getResponseBody();
        assertThat(clamped).hasSize(MAX_CONVERSATION_LIMIT_FIXTURE);

        // A non-positive limit must clamp to a single row rather than throwing from PageRequest.of.
        var floored = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/users/me/assistant/conversations")
                        .queryParam("limit", 0)
                        .build())
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserAssistantConversationSummaryResponse[].class)
                .returnResult()
                .getResponseBody();
        assertThat(floored).hasSize(1);
    }

    @Test
    void publicHealthEndpointStaysOpen() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    private static UserProductSearchQueryIntentResult queryIntent(String normalizedQuery, String displayQuery) {
        return new UserProductSearchQueryIntentResult(
                normalizedQuery,
                normalizedQuery,
                normalizedQuery,
                normalizedQuery,
                displayQuery,
                normalizedQuery,
                normalizedQuery,
                List.of(),
                List.of(),
                "high",
                "deterministic"
        );
    }

    private static MerchantSemanticProductResult recentSearchProduct() {
        return new MerchantSemanticProductResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://merchant.example/mcp",
                1,
                0.9d,
                0.8d,
                "tee",
                "Organic Cotton Tee",
                "<p>Organic cotton tee with no polyester.</p>",
                "https://merchant.example/products/tee",
                "https://merchant.example/tee.jpg",
                3800L,
                3800L,
                "USD",
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                null,
                "Organic cotton tee with no polyester.",
                "https://merchant.example/tee.jpg",
                List.of(),
                List.of(),
                "38.00",
                "38.00",
                "USD",
                1,
                false,
                List.of(),
                "variant-1",
                "Default",
                List.of(),
                "38.00",
                "USD",
                "https://merchant.example/tee.jpg",
                "Tee",
                true,
                1,
                0.92d,
                1
        );
    }
}
