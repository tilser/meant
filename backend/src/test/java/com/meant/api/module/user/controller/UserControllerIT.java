package com.meant.api.module.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.PostgresIntegrationTestSupport;
import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.cart.controller.response.CartResponse;
import com.meant.api.module.cart.service.MerchantCartPluginDispatchService;
import com.meant.api.module.cart.service.SelectedOfferCartRoutingService;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.merchant.exception.MerchantCatalogSearchException;
import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.entity.MerchantIntegration;
import com.meant.api.module.merchant.entity.MerchantRaw;
import com.meant.api.module.merchant.repository.MerchantIntegrationRepository;
import com.meant.api.module.merchant.repository.MerchantRawRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.service.MerchantSemanticProductSearchService;
import com.meant.api.module.merchant.service.MerchantIntegrationLookupService;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByMerchantsQuery;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsQuery;
import com.meant.api.module.merchant.service.MerchantProductDetailsService;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.support.UcpSession;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import com.meant.api.module.user.controller.response.UserAssistantConversationSummaryResponse;
import com.meant.api.module.user.controller.response.UserInventoryExportResponse;
import com.meant.api.module.user.controller.response.UserInventoryItemResponse;
import com.meant.api.module.user.controller.response.UserPopularProductSearchResponse;
import com.meant.api.module.user.controller.response.UserProductDiscoveryResponse;
import com.meant.api.module.user.controller.response.UserProductSearchProductResponse;
import com.meant.api.module.user.controller.response.UserResponse;
import com.meant.api.module.user.controller.response.UserSavedProductResponse;
import com.meant.api.module.user.controller.response.UserSettingsResponse;
import com.meant.api.module.user.controller.response.UserTasteProfileResponse;
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
import com.meant.api.module.user.service.UserInventoryService;
import com.meant.api.module.user.service.UserProductSearchHashService;
import com.meant.api.module.user.service.UserSelectedOfferResolutionService;
import com.meant.api.module.user.service.UserSettingsService;
import com.meant.api.module.user.service.UserTasteProfileService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.ResolveUserSelectedOfferQuery;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.IntStream;
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
class UserControllerIT extends PostgresIntegrationTestSupport {

    /** Mirrors the assistant conversations controller limit; kept local to avoid exposing the constant. */
    private static final int MAX_CONVERSATION_LIMIT_FIXTURE = 50;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserInventoryService userInventoryService;

    @Autowired
    private UserTasteProfileService userTasteProfileService;

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

    @Autowired
    private CatalogDataUsePolicyResolver catalogDataUsePolicyResolver;

    @Autowired
    private UserSelectedOfferResolutionService userSelectedOfferResolutionService;

    @Autowired
    private MerchantRawRepository merchantRawRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private MerchantIntegrationRepository merchantIntegrationRepository;

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
                    if ("product_recommendation_explanations".equals(schemaName)) {
                        return """
                                {
                                  "products": [
                                    {
                                      "productKey": "merchant.example:tee",
                                      "whyMeantForYou": "Organic cotton and no polyester match your profile.",
                                      "matchedFilterIds": ["organic"],
                                      "missedFilterIds": []
                                    }
                                  ]
                                }
                                """;
                    }
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

        @Bean
        @Primary
        MerchantIntegrationLookupService testMerchantIntegrationLookupService(
                MerchantIntegrationRepository merchantIntegrationRepository
        ) {
            return new MerchantIntegrationLookupService(merchantIntegrationRepository) {
                @Override
                public List<MerchantIntegrationResult> listByMerchant(ListMerchantIntegrationsQuery query) {
                    List<MerchantIntegrationResult> persisted = super.listByMerchant(query);
                    return persisted.isEmpty() ? List.of(integration(query.merchantId())) : persisted;
                }

                @Override
                public List<MerchantIntegrationResult> listByMerchants(
                        ListMerchantIntegrationsByMerchantsQuery query
                ) {
                    List<MerchantIntegrationResult> persisted = super.listByMerchants(query);
                    return query.merchantIds().stream()
                            .flatMap(merchantId -> {
                                List<MerchantIntegrationResult> matches = persisted.stream()
                                        .filter(integration -> integration.merchantId().equals(merchantId))
                                        .toList();
                                return (matches.isEmpty() ? List.of(integration(merchantId)) : matches).stream();
                            })
                            .toList();
                }

                private MerchantIntegrationResult integration(UUID merchantId) {
                    Instant now = Instant.parse("2026-07-11T00:00:00Z");
                    return new MerchantIntegrationResult(
                            UUID.fromString("00000000-0000-0000-0000-000000000097"),
                            merchantId,
                            MerchantIntegrationProvider.GENERIC_UCP,
                            MerchantIntegrationKind.MERCHANT_CONNECTION,
                            Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG),
                            null,
                            "merchant.example",
                            null,
                            "https://merchant.example/mcp",
                            "2026-04-08",
                            MerchantIntegrationAuthStrategy.NONE,
                            MerchantIntegrationStatus.ACTIVE,
                            MerchantIntegrationSource.MANUAL,
                            now,
                            now,
                            now
                    );
                }
            };
        }

        @Bean
        @Primary
        MerchantSemanticProductSearchService testMerchantSemanticProductSearchService() {
            return new MerchantSemanticProductSearchService(null, null, null, null, null, null, null) {
                @Override
                public MerchantSemanticProductSearchResult search(SemanticProductSearchQuery query) {
                    return search(query, null);
                }

                @Override
                public MerchantSemanticProductSearchResult search(
                        SemanticProductSearchQuery query,
                        Consumer<MerchantSemanticProductResult> candidateConsumer
                ) {
                    if (query.merchantId() != null) {
                        throw MerchantCatalogSearchException.notFound("Active merchant not found: " + query.merchantId());
                    }
                    MerchantSemanticProductResult product = recentSearchProduct();
                    if (candidateConsumer != null) {
                        candidateConsumer.accept(product);
                        candidateConsumer.accept(product);
                    }
                    return new MerchantSemanticProductSearchResult(List.of(), List.of(product));
                }
            };
        }

        @Bean
        @Primary
        MerchantProductDetailsService testMerchantProductDetailsService() {
            return new MerchantProductDetailsService(null, null) {
                @Override
                public ProductDetailsResult get(GetMerchantProductDetailsQuery query) {
                    return new ProductDetailsResult(
                            "https://merchant.example/mcp",
                            "redacted",
                            new ProductDetailsResponse.Product(
                                    query.productId(),
                                    "Current saved product",
                                    "Current description",
                                    "https://merchant.example/products/current",
                                    "https://merchant.example/media/current.jpg",
                                    List.of(),
                                    List.of(),
                                    1,
                                    new ProductDetailsResponse.PriceRange("7.40", "7.40", "USD"),
                                    false,
                                    List.of(),
                                    new ProductDetailsResponse.SelectedVariant(
                                            "variant-1",
                                            "Default",
                                            "7.40",
                                            "USD",
                                            "https://merchant.example/media/current.jpg",
                                            "Current product",
                                            true,
                                            List.of()
                                    )
                            )
                    );
                }
            };
        }

        @Bean
        @Primary
        SelectedOfferCartRoutingService testSelectedOfferCartRoutingService() {
            SelectedOfferCartRoutingService service = mock(SelectedOfferCartRoutingService.class);
            when(service.resolve(any(ResolvedSelectedOffer.class))).thenAnswer(invocation -> {
                ResolvedSelectedOffer offer = invocation.getArgument(0);
                MerchantIntegrationProvider provider = MerchantIntegrationProvider.valueOf(
                        offer.identity().provider().value());
                var externalMerchant = offer.identity().merchantScope().externalMerchantIdentity();
                String externalMerchantId = externalMerchant == null ? null : externalMerchant.value();
                var localRouting = offer.rehydratedReference().localRouting();
                MerchantCartProvider merchantProvider = new MerchantCartProvider(
                        offer.rehydratedReference().localMerchantId(),
                        "shop.example",
                        "https://cart.test/mcp",
                        null
                );
                String scopeKey = localRouting == null
                        ? "test:" + provider + ":" + externalMerchantId
                        : "test:" + provider + ":" + localRouting.merchantIntegrationId();
                return new CartRoutingTarget(
                        scopeKey,
                        provider,
                        localRouting == null ? null : localRouting.merchantIntegrationId(),
                        externalMerchantId,
                        merchantProvider
                );
            });
            return service;
        }

        @Bean
        @Primary
        MerchantCartPluginDispatchService testMerchantCartPluginDispatchService() {
            MerchantCartPluginDispatchService service = mock(MerchantCartPluginDispatchService.class);
            when(service.createCart(
                    any(CartRoutingTarget.class), any(CreateCartRequest.class), any(UcpSession.class)))
                    .thenAnswer(invocation -> {
                        CreateCartRequest request = invocation.getArgument(1);
                        assertThat(request.addItems()).singleElement().satisfies(item -> {
                            assertThat(item.productVariantId()).isEqualTo("variant-1");
                            assertThat(item.quantity()).isEqualTo(2);
                        });
                        Instant now = Instant.now();
                        UcpCartResponse.Line line = new UcpCartResponse.Line(
                                "remote-line-1",
                                2,
                                new UcpCartResponse.Cost(
                                        new UcpCartResponse.Money("14.80", "USD"),
                                        new UcpCartResponse.Money("14.80", "USD")
                                ),
                                new UcpCartResponse.Merchandise(
                                        "variant-1",
                                        "Default",
                                        new UcpCartResponse.Product(
                                                "gid://shopify/Product/123", "Current saved product"),
                                        "gid://shopify/Product/123",
                                        List.of(),
                                        List.of(),
                                        null
                                )
                        );
                        UcpCartResponse response = new UcpCartResponse(
                                null,
                                new UcpCartResponse.Cart(
                                        "remote-saved-cart-1",
                                        now,
                                        now,
                                        now.plusSeconds(3600),
                                        List.of(line),
                                        new UcpCartResponse.Cost(
                                                new UcpCartResponse.Money("14.80", "USD"),
                                                new UcpCartResponse.Money("14.80", "USD")
                                        ),
                                        2,
                                        "https://shop.example/checkout/remote-saved-cart-1",
                                        null,
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of()
                                ),
                                List.of(),
                                List.of()
                        );
                        return new UcpCartToolResult("https://cart.test/mcp", "{}", response);
                    });
            return service;
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
        assertThat(body.newsletter()).isFalse();
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
    void patchUpdatesNewsletterSubscription() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";

        UserResponse subscribed = client.patch().uri("/api/users/me/newsletter")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, null));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("{\"newsletter\":true}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(subscribed).isNotNull();
        assertThat(subscribed.newsletter()).isTrue();
        assertThat(userRepository.findById(id).orElseThrow().isNewsletter()).isTrue();

        UserResponse unsubscribed = client.patch().uri("/api/users/me/newsletter")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, null));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("{\"newsletter\":false}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(unsubscribed).isNotNull();
        assertThat(unsubscribed.newsletter()).isFalse();
        assertThat(userRepository.findById(id).orElseThrow().isNewsletter()).isFalse();
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
    void tasteProfileHandlesConcurrentFirstSettingsCreation() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        String bearer = token(id, email, "Ada Lovelace");
        int requestCount = 8;

        try (ExecutorService executor = Executors.newFixedThreadPool(requestCount)) {
            List<CompletableFuture<UserTasteProfileResponse>> futures = IntStream.range(0, requestCount)
                    .mapToObj(_ -> CompletableFuture.supplyAsync(() -> client.get().uri("/api/users/me/taste-profile")
                            .headers(headers -> headers.setBearerAuth(bearer))
                            .exchange()
                            .expectStatus().isOk()
                            .expectBody(UserTasteProfileResponse.class)
                            .returnResult()
                            .getResponseBody(), executor))
                    .toList();

            List<UserTasteProfileResponse> responses = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            assertThat(responses).hasSize(requestCount)
                    .allSatisfy(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.profileHash()).isNotBlank();
                    });
        }

        UserSettingsResponse settings = client.get().uri("/api/users/me/settings")
                .headers(headers -> headers.setBearerAuth(bearer))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserSettingsResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(settings).isNotNull();
        assertThat(settings.filters()).extracting("id")
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
    void streamProductSearchesReturnsDiscoveryThenCuratorProgressionAndDoneEvent() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        String query = "organic cotton tee";
        String userAgent = "meant-stream-test";

        String body = client.post().uri("/api/users/me/product-searches:stream")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, "Ada Lovelace"));
                    headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("User-Agent", userAgent);
                })
                .body("""
                        {
                          "query": "%s",
                          "offset": 0,
                          "limit": 3
                        }
                        """.formatted(query))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body).contains(
                "\"type\":\"phase\"",
                "\"agent\":\"discovery\"",
                "\"type\":\"product\"",
                "\"label\":\"Product candidate found\"",
                "\"type\":\"product_update\"",
                "\"label\":\"Product details updated\"",
                "\"agent\":\"curator\"",
                "\"label\":\"Curator score updated\"",
                "\"type\":\"rank_update\"",
                "\"label\":\"Curator order updated\"",
                "\"type\":\"done\"",
                "\"label\":\"Curated results ready\"",
                "\"title\":\"Organic Cotton Tee\"",
                "\"cached\":false",
                "\"hasMore\":false"
        );
        assertThat(body).doesNotContain(
                "\"agent\":\"query\"",
                "\"agent\":\"profile\"",
                "\"agent\":\"catalog\"",
                "\"agent\":\"taste\"",
                "\"agent\":\"reasoning\"",
                "\"agent\":\"ranking\""
        );
        assertBefore(body, "\"agent\":\"discovery\"", "\"type\":\"product\"");
        assertBefore(body, "\"label\":\"Product candidate found\"", "\"label\":\"Product details updated\"");
        assertBefore(body, "\"label\":\"Product details updated\"", "\"agent\":\"curator\"");
        assertBefore(body, "\"label\":\"Curator score updated\"", "\"type\":\"rank_update\"");
        assertBefore(body, "\"type\":\"rank_update\"", "\"type\":\"done\"");
    }

    @Test
    void streamProductSearchesEmitsErrorEventWhenSearchFails() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";

        String body = client.post().uri("/api/users/me/product-searches:stream")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, "Ada Lovelace"));
                    headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "query": "organic cotton tee",
                          "merchantId": "%s",
                          "offset": 0,
                          "limit": 3
                        }
                        """.formatted(UUID.randomUUID()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body).contains(
                "\"type\":\"phase\"",
                "\"agent\":\"discovery\"",
                "\"type\":\"error\"",
                "\"agent\":\"search\"",
                "\"message\":\"Product search failed. Please try again.\""
        );
        assertBefore(body, "\"agent\":\"discovery\"", "\"type\":\"error\"");
    }

    @Test
    void savedProductsCanBeSavedListedAndRemoved() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        String productKey = "shop.example:gid://shopify/Product/123";
        EnsureUserProfileCommand profileCommand = new EnsureUserProfileCommand(id, email, "Ada", "Lovelace");
        Instant now = Instant.now();
        String profileHash = searchProfileHash(id, profileCommand);
        UserProductSearch search = userProductSearchRepository.save(UserProductSearch.create(
                id,
                "saved cereal",
                "saved cereal",
                profileHash,
                userProductSearchProperties.searchVersion(),
                now,
                now.plusSeconds(3600),
                currentSearchPolicyFingerprint(),
                false
        ));
        MerchantSemanticProductResult savedProduct = recentSearchProduct(
                "1",
                "Saved Cereal",
                "Organic and low sugar.",
                740L,
                1,
                0.96d
        );
        saveCartMerchant(savedProduct.merchantId());
        saveRecentProduct(
                id,
                profileHash,
                search,
                productKey,
                "hash-1",
                savedProduct,
                "Organic and low sugar.",
                now
        );

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
        assertThat(saved.commercialFactsAuthoritative()).isFalse();
        assertThat(saved.name()).isEqualTo("Saved Cereal");
        assertThat(saved.priceFrom()).isNull();
        assertThat(saved.imageUrl()).isEqualTo("https://example.com/cereal.png");
        assertThat(saved.details()).isNull();

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
        assertThat(listed[0].details()).isNull();

        UserSavedProductResponse detail = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/users/me/saved-products/detail")
                        .queryParam("productKey", productKey)
                        .build())
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserSavedProductResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(detail).isNotNull();
        assertThat(detail.id()).isEqualTo(productKey);
        assertThat(detail.name()).isNotBlank();
        assertThat(detail.commercialFactsAuthoritative()).isTrue();
        assertThat(detail.details()).isNotNull();
        assertThat(detail.details().description()).isEqualTo("Current description");
        assertThat(detail.details().imageUrl()).isEqualTo("https://merchant.example/media/current.jpg");
        assertThat(detail.details().selectedVariantId()).isEqualTo("variant-1");
        assertThat(detail.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.offerKey()).isNotBlank();
            assertThat(offer.productVariantId()).isEqualTo("variant-1");
        });

        String savedOfferKey = detail.offers().getFirst().offerKey();
        ResolvedSelectedOffer cartSelection = userSelectedOfferResolutionService.resolve(
                new ResolveUserSelectedOfferQuery(id, savedOfferKey));
        assertThat(cartSelection.canonicalProductKey()).isEqualTo(productKey);
        assertThat(cartSelection.offerKey()).isEqualTo(savedOfferKey);
        assertThat(cartSelection.identity().externalVariantIdentity().value()).isEqualTo("variant-1");

        CartResponse cart = client.post().uri("/api/carts")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, "Ada Lovelace"));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "addItems": [
                            {
                              "offerKey": "%s",
                              "quantity": 2
                            }
                          ],
                          "discountCodes": [],
                          "giftCardCodes": []
                        }
                        """.formatted(savedOfferKey))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CartResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(cart).isNotNull();
        assertThat(cart.remoteCartId()).isEqualTo("remote-saved-cart-1");
        assertThat(cart.totalQuantity()).isEqualTo(2);
        assertThat(cart.lines()).singleElement().satisfies(line -> {
            assertThat(line.offerKey()).isEqualTo(savedOfferKey);
            assertThat(line.productVariantId()).isEqualTo("variant-1");
            assertThat(line.quantity()).isEqualTo(2);
        });

        UUID otherUserId = UUID.randomUUID();
        client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/users/me/saved-products/detail")
                        .queryParam("productKey", productKey)
                        .build())
                .headers(headers -> headers.setBearerAuth(token(
                        otherUserId, otherUserId + "@example.com", "Grace Hopper")))
                .exchange()
                .expectStatus().isNotFound();

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
        EnsureUserProfileCommand profileCommand = new EnsureUserProfileCommand(id, email, "Ada", "Lovelace");
        String profileHash = searchProfileHash(id, profileCommand);
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
                currentSearchPolicyFingerprint(),
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
    void productDiscoverySearchesAndSortsRecentProducts() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        EnsureUserProfileCommand profileCommand = new EnsureUserProfileCommand(id, email, "Ada", "Lovelace");
        String profileHash = searchProfileHash(id, profileCommand);
        Instant now = Instant.now();

        UserProductSearch search = userProductSearchRepository.save(UserProductSearch.create(
                id,
                "organic basics",
                "organic basics",
                profileHash,
                userProductSearchProperties.searchVersion(),
                now,
                now.plusSeconds(3600),
                currentSearchPolicyFingerprint(),
                false
        ));
        saveRecentProduct(
                id,
                profileHash,
                search,
                "merchant.example:linen",
                "hash-linen",
                recentSearchProduct("linen", "Organic Linen Shirt", "Organic linen button-down.", 6200L, 1, 0.62d),
                "Organic linen matches your material preferences.",
                now
        );
        saveRecentProduct(
                id,
                profileHash,
                search,
                "merchant.example:cap",
                "hash-cap",
                recentSearchProduct("cap", "Organic Cotton Cap", "Organic cotton cap.", 2400L, 2, 0.72d),
                "Organic cotton and no polyester match your profile.",
                now
        );
        saveRecentProduct(
                id,
                profileHash,
                search,
                "merchant.example:socks",
                "hash-socks",
                recentSearchProduct("socks", "Merino Wool Socks", "Warm wool socks.", 1800L, 3, 0.82d),
                "Wool socks match your profile.",
                now
        );

        UserProductDiscoveryResponse filtered = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/users/me/product-discovery")
                        .queryParam("search", "organic")
                        .queryParam("sortBy", "price")
                        .queryParam("sortDirection", "asc")
                        .build())
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserProductDiscoveryResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(filtered).isNotNull();
        assertThat(filtered.recentProducts()).extracting(UserProductSearchProductResponse::title)
                .containsExactly("Organic Cotton Cap", "Organic Linen Shirt");

        UserProductDiscoveryResponse sortedByName = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/users/me/product-discovery")
                        .queryParam("sortBy", "name")
                        .queryParam("sortDirection", "asc")
                        .build())
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserProductDiscoveryResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(sortedByName).isNotNull();
        assertThat(sortedByName.recentProducts()).extracting(UserProductSearchProductResponse::title)
                .containsExactly("Merino Wool Socks", "Organic Cotton Cap", "Organic Linen Shirt");
    }

    @Test
    void productDiscoverySearchKeepsRecentProductWhenSavedTwinIsFilteredOut() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        EnsureUserProfileCommand profileCommand = new EnsureUserProfileCommand(id, email, "Ada", "Lovelace");
        Instant now = Instant.now();
        String productKey = "merchant.example:organic-tee";
        String profileHash = searchProfileHash(id, profileCommand);
        UserProductSearch search = userProductSearchRepository.save(UserProductSearch.create(
                id,
                "organic basics",
                "organic basics",
                profileHash,
                userProductSearchProperties.searchVersion(),
                now,
                now.plusSeconds(3600),
                currentSearchPolicyFingerprint(),
                false
        ));
        saveRecentProduct(
                id,
                profileHash,
                search,
                productKey,
                "hash-tee",
                recentSearchProduct("tee", "Organic Cotton Tee", "Organic cotton tee.", 3800L, 1, 0.9d),
                "Organic cotton matches your profile.",
                now
        );

        // A saved product whose text does NOT contain "organic" so it is filtered out by the search.
        client.post().uri("/api/users/me/saved-products")
                .headers(headers -> {
                    headers.setBearerAuth(token(id, email, "Ada Lovelace"));
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .body("""
                        {
                          "id": "%s",
                          "productHash": "hash-tee",
                          "name": "Plain Tee",
                          "brand": "Basics",
                          "category": "Apparel",
                          "tone": "#ffffff",
                          "imageUrl": "https://example.com/tee.png",
                          "productUrl": "https://merchant.example/products/tee",
                          "remote": true,
                          "match": 80,
                          "priceFrom": 38.0,
                          "merchants": 1,
                          "satisfies": [],
                          "misses": [],
                          "note": "Comfortable everyday tee.",
                          "pros": [],
                          "cons": [],
                          "review": { "score": 4.2, "count": 10, "insight": "Comfortable." },
                          "offers": [
                            {
                              "merchant": "Basics Store",
                              "price": 38.0,
                              "delivery": "Tomorrow",
                              "merchantId": "merchant-tee",
                              "merchantDomain": "merchant.example",
                              "productVariantId": "variant-tee",
                              "variantTitle": "Default",
                              "available": true
                            }
                          ],
                          "needs": null,
                          "provides": []
                        }
                        """.formatted(productKey))
                .exchange()
                .expectStatus().isOk();

        // The recent twin (same productKey) DOES match "organic", so it must still surface.
        UserProductDiscoveryResponse discovery = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/users/me/product-discovery")
                        .queryParam("search", "organic")
                        .build())
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserProductDiscoveryResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(discovery).isNotNull();
        assertThat(discovery.savedProducts()).isEmpty();
        assertThat(discovery.recentProducts()).extracting(UserProductSearchProductResponse::title)
                .containsExactly("Organic Cotton Tee");
    }

    @Test
    void productDiscoverySortsRecentProductsWithMissingRatingLastInBothDirections() {
        UUID id = UUID.randomUUID();
        String email = id + "@example.com";
        EnsureUserProfileCommand profileCommand = new EnsureUserProfileCommand(id, email, "Ada", "Lovelace");
        String profileHash = searchProfileHash(id, profileCommand);
        Instant now = Instant.now();

        UserProductSearch search = userProductSearchRepository.save(UserProductSearch.create(
                id,
                "organic basics",
                "organic basics",
                profileHash,
                userProductSearchProperties.searchVersion(),
                now,
                now.plusSeconds(3600),
                currentSearchPolicyFingerprint(),
                false
        ));
        saveRecentProduct(id, profileHash, search, "merchant.example:rated-high", "hash-high",
                recentSearchProduct("high", "Rated High", "High rated.", 5000L, 1, 0.9d, 4.8d),
                "Top rated.", now);
        saveRecentProduct(id, profileHash, search, "merchant.example:rated-low", "hash-low",
                recentSearchProduct("low", "Rated Low", "Low rated.", 5000L, 2, 0.9d, 3.1d),
                "Lower rated.", now);
        saveRecentProduct(id, profileHash, search, "merchant.example:unrated", "hash-unrated",
                recentSearchProduct("unrated", "Unrated", "No rating.", 5000L, 3, 0.9d, null),
                "No rating yet.", now);

        UserProductDiscoveryResponse desc = sortByRating(id, email, "desc");
        assertThat(desc).isNotNull();
        assertThat(desc.recentProducts()).extracting(UserProductSearchProductResponse::title)
                .containsExactly("Rated High", "Rated Low", "Unrated");

        UserProductDiscoveryResponse asc = sortByRating(id, email, "asc");
        assertThat(asc).isNotNull();
        assertThat(asc.recentProducts()).extracting(UserProductSearchProductResponse::title)
                .containsExactly("Rated Low", "Rated High", "Unrated");
    }

    private UserProductDiscoveryResponse sortByRating(UUID id, String email, String direction) {
        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/users/me/product-discovery")
                        .queryParam("sortBy", "rating")
                        .queryParam("sortDirection", direction)
                        .build())
                .headers(headers -> headers.setBearerAuth(token(id, email, "Ada Lovelace")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserProductDiscoveryResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void popularProductSearchesReturnsDistinctUserAggregates() {
        Instant now = Instant.now();
        String displayQuery = "Organic cotton T-shirt under $50";
        for (int index = 0; index < 3; index++) {
            UUID userId = UUID.randomUUID();
            String email = userId + "@example.com";
            userSettingsService.get(new EnsureUserProfileCommand(userId, email, "User", String.valueOf(index)));
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
        userSettingsService.get(new EnsureUserProfileCommand(scopedUserId, scopedUserId + "@example.com", "Scoped", "User"));
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
        // Consumption). The endpoint ensures the user profile on read, so no users row is required up front.
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

    @Test
    void openApiPublishesFederatedGroupedAndStreamingV1WithoutReplacingFlatRoutes() {
        String openApi = client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(openApi)
                .contains(
                        "\"/api/users/me/product-searches\"",
                        "\"/api/users/me/saved-products/detail\"",
                        "\"/api/users/me/product-searches:stream\"",
                        "\"/api/v1/users/me/product-searches\"",
                        "\"/api/v1/users/me/product-searches:stream\"",
                        "\"/api/v1/users/me/product-variant-selections\"",
                        "\"operationId\":\"searchProducts\"",
                        "\"operationId\":\"searchGroupedProductsV1\"",
                        "\"operationId\":\"streamFederatedProductsV1\"",
                        "UserGroupedProductSearchV1Response",
                        "UserFederatedProductSearchStreamEventResponse",
                        "SelectUserProductVariantRequest",
                        "UserProductVariantSelectionResponse",
                        "CanonicalProductResponse",
                        "CanonicalProductAttributeResponse",
                        "OfferResponse",
                        "OfferMerchantScopeResponse",
                        "ProductGroupingDecisionResponse",
                        "DiscoverySourceIdentityResponse",
                        "LocalMerchantRoutingResponse",
                        "ResultProvenanceResponse",
                        "CatalogReference",
                        "SelectedOption",
                        "UserSavedProductReview",
                        "UserSavedProductOffer",
                        "commercialFactsAuthoritative",
                        "\"minorUnits\"",
                        "\"groupingDecisions\"",
                        "\"CONTRADICTION_VETO\""
                );
    }

    private static void assertBefore(String value, String first, String second) {
        assertThat(value).contains(first, second);
        assertThat(value.indexOf(first)).isLessThan(value.indexOf(second));
    }

    private String currentSearchPolicyFingerprint() {
        return catalogDataUsePolicyResolver.admitSearch(List.of(MerchantCatalogSourceIdentity.DISCOVERY_SOURCE))
                .policyFingerprint();
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

    private String searchProfileHash(UUID userId, EnsureUserProfileCommand profileCommand) {
        UserSettingsResult settings = userSettingsService.get(profileCommand);
        return userProductSearchHashService.searchProfileHash(
                settings,
                userInventoryService.inventoryProfileHash(userId),
                userTasteProfileService.profile(userId, settings).profileHash()
        );
    }

    private static MerchantSemanticProductResult recentSearchProduct() {
        return recentSearchProduct("tee", "Organic Cotton Tee", "Organic cotton tee with no polyester.", 3800L, 1, 0.92d);
    }

    private void saveRecentProduct(
            UUID userId,
            String profileHash,
            UserProductSearch search,
            String productKey,
            String productHash,
            MerchantSemanticProductResult product,
            String whyMeantForYou,
            Instant now
    ) {
        userProductSearchResultItemRepository.save(UserProductSearchResultItem.from(
                search.getId(),
                productKey,
                productHash,
                product,
                now
        ));
        userProductRecommendationExplanationRepository.save(UserProductRecommendationExplanation.create(
                userId,
                search.getNormalizedQuery(),
                profileHash,
                productKey,
                productHash,
                openRouterProperties.models().productRecommendationExplainer(),
                userProductSearchProperties.explanationPromptVersion(),
                whyMeantForYou,
                now
        ));
    }

    private static MerchantSemanticProductResult recentSearchProduct(
            String productId,
            String title,
            String description,
            Long price,
            int rank,
            double productRerankScore
    ) {
        return recentSearchProduct(productId, title, description, price, rank, productRerankScore, null);
    }

    private static MerchantSemanticProductResult recentSearchProduct(
            String productId,
            String title,
            String description,
            Long price,
            int rank,
            double productRerankScore,
            Double ratingScore
    ) {
        return new MerchantSemanticProductResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://merchant.example/mcp",
                1,
                0.9d,
                0.8d,
                productId,
                title,
                "<p>%s</p>".formatted(description),
                "https://merchant.example/products/" + productId,
                "https://merchant.example/" + productId + ".jpg",
                price,
                price,
                "USD",
                null,
                null,
                ratingScore,
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
                description,
                "https://merchant.example/" + productId + ".jpg",
                List.of(),
                List.of(),
                "%.2f".formatted(price / 100.0d),
                "%.2f".formatted(price / 100.0d),
                "USD",
                1,
                false,
                List.of(),
                "variant-" + productId,
                "Default",
                List.of(),
                "%.2f".formatted(price / 100.0d),
                "USD",
                "https://merchant.example/" + productId + ".jpg",
                title,
                true,
                rank,
                productRerankScore,
                rank
        );
    }

    private void saveCartMerchant(UUID merchantId) {
        Instant now = Instant.parse("2026-07-11T00:00:00Z");
        MerchantRaw raw = merchantRawRepository.save(MerchantRaw.builder()
                .id(UUID.randomUUID())
                .datasetRowIdx(Math.abs(merchantId.hashCode()))
                .domain("merchant.example")
                .status("OK")
                .ucpUrl("https://merchant.example/.well-known/ucp.json")
                .httpStatus(200)
                .ucpVersion("2026-04-08")
                .hasCheckout(true)
                .hasIdentityLinking(false)
                .hasCartManagement(true)
                .hasOrder(false)
                .hasPaymentToken(false)
                .capabilityCount(2)
                .transports("[]")
                .fetchedAt(now)
                .processed(true)
                .processingStatus("SUCCESS")
                .sourceHash("saved-product-cart-" + merchantId)
                .active(true)
                .lastSeenAt(now)
                .build());
        Merchant merchant = merchantRepository.save(Merchant.builder()
                .id(merchantId)
                .merchantRaw(raw)
                .domain("merchant.example")
                .ucpUrl(raw.getUcpUrl())
                .ucpVersion(raw.getUcpVersion())
                .advertisedMcpEndpoint("https://merchant.example/mcp")
                .profileHash("saved-product-cart-" + merchantId)
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
        merchantIntegrationRepository.save(MerchantIntegration.builder()
                .merchant(merchant)
                .provider(MerchantIntegrationProvider.GENERIC_UCP)
                .kind(MerchantIntegrationKind.MERCHANT_CONNECTION)
                .roles(Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG, MerchantIntegrationRole.CART))
                .verifiedDomain("merchant.example")
                .endpoint("https://merchant.example/mcp")
                .protocolVersion("2026-04-08")
                .authStrategy(MerchantIntegrationAuthStrategy.NONE)
                .status(MerchantIntegrationStatus.ACTIVE)
                .source(MerchantIntegrationSource.MANUAL)
                .capturedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
