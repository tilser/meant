package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class UserProductRecommendationExplanationServiceTest {

    @Test
    void explainGeneratesSanitizesAndStoresMissingExplanations() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        FakeUserProductSearchPersistenceService persistenceService = new FakeUserProductSearchPersistenceService();
        UserProductRecommendationExplanationService service = service(openRouterChatClient, persistenceService);
        openRouterChatClient.response = """
                {
                  "products": [
                    {
                      "productKey": "merchant.example:tee",
                      "whyMeantForYou": "  Organic cotton and no polyester match your profile.  ",
                      "matchedFilterIds": ["organic-cotton", "unknown", "organic-cotton"],
                      "missedFilterIds": ["no-polyester", "crypto"]
                    }
                  ]
                }
                """;

        Map<String, UserProductRecommendationExplanationResult> result = service.explain(
                UUID.randomUUID(),
                "cotton tee",
                "cotton tee",
                "profile-hash",
                settings(),
                List.of(snapshot())
        );

        assertThat(openRouterChatClient.model).isEqualTo("google/gemini-2.5-flash-lite");
        assertThat(openRouterChatClient.systemPrompt)
                .contains("Write directly to the user")
                .contains("Use exact productKey values from the product list")
                .contains("Do not mention internal agents, curator, catalog data, validation, or verification steps");
        assertThat(openRouterChatClient.schema.properties()).containsKey("products");
        assertProviderSafeSchema(openRouterChatClient.schema);
        assertThat(persistenceService.saved).hasSize(1);
        assertThat(result.get("merchant.example:tee").whyMeantForYou())
                .isEqualTo("Organic cotton and no polyester match your profile.");
        assertThat(result.get("merchant.example:tee").matchedFilterIds())
                .containsExactly("organic-cotton");
        assertThat(result.get("merchant.example:tee").missedFilterIds())
                .containsExactly("no-polyester");
    }

    @Test
    void explainParsesFencedOpenRouterJson() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        FakeUserProductSearchPersistenceService persistenceService = new FakeUserProductSearchPersistenceService();
        UserProductRecommendationExplanationService service = service(openRouterChatClient, persistenceService);
        openRouterChatClient.response = """
                ```json
                {
                  "products": [
                    {
                      "productKey": "merchant.example:tee",
                      "whyMeantForYou": "Organic cotton matches your profile.",
                      "matchedFilterIds": ["organic-cotton"],
                      "missedFilterIds": []
                    }
                  ]
                }
                ```
                """;

        Map<String, UserProductRecommendationExplanationResult> result = service.explain(
                UUID.randomUUID(),
                "cotton tee",
                "cotton tee",
                "profile-hash",
                settings(),
                List.of(snapshot())
        );

        assertThat(result.get("merchant.example:tee").whyMeantForYou())
                .isEqualTo("Organic cotton matches your profile.");
        assertThat(persistenceService.saved).singleElement()
                .extracting(UserProductRecommendationExplanationResult::productKey)
                .isEqualTo("merchant.example:tee");
    }

    @Test
    void explainUsesCachedExplanationWithoutCallingOpenRouter() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        FakeUserProductSearchPersistenceService persistenceService = new FakeUserProductSearchPersistenceService();
        UserProductRecommendationExplanationResult cached = new UserProductRecommendationExplanationResult(
                "merchant.example:tee",
                "product-hash",
                "Cached explanation",
                List.of("organic-cotton"),
                List.of()
        );
        persistenceService.cached.put(cached.productKey(), cached);
        UserProductRecommendationExplanationService service = service(openRouterChatClient, persistenceService);

        Map<String, UserProductRecommendationExplanationResult> result = service.explain(
                UUID.randomUUID(),
                "cotton tee",
                "cotton tee",
                "profile-hash",
                settings(),
                List.of(snapshot())
        );

        assertThat(openRouterChatClient.called).isFalse();
        assertThat(result.get("merchant.example:tee")).isEqualTo(cached);
    }

    @Test
    void explainAddsInventorySignalToPromptAndResult() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        FakeUserProductSearchPersistenceService persistenceService = new FakeUserProductSearchPersistenceService();
        UserProductRecommendationExplanationService service = service(openRouterChatClient, persistenceService);
        UUID inventoryItemId = UUID.randomUUID();
        openRouterChatClient.response = """
                {
                  "products": [
                    {
                      "productKey": "merchant.example:tee",
                      "whyMeantForYou": "It may duplicate your owned organic cotton tee.",
                      "matchedFilterIds": ["organic-cotton"],
                      "missedFilterIds": []
                    }
                  ]
                }
                """;

        Map<String, UserProductRecommendationExplanationResult> result = service.explain(
                UUID.randomUUID(),
                "cotton tee",
                "cotton tee",
                "profile-hash",
                settings(),
                List.of(snapshot()),
                Map.of("merchant.example:tee", new UserInventoryRecommendationSignal(
                        "merchant.example:tee",
                        UserInventoryRecommendationRelationship.DUPLICATE,
                        inventoryItemId,
                        "Owned cotton tee",
                        "Looks similar to Owned cotton tee already in inventory"
                ))
        );

        assertThat(openRouterChatClient.userPrompt).contains("Owned inventory signal: DUPLICATE");
        assertThat(result.get("merchant.example:tee").inventoryRelationship())
                .isEqualTo(UserInventoryRecommendationRelationship.DUPLICATE);
        assertThat(result.get("merchant.example:tee").inventoryItemId()).isEqualTo(inventoryItemId);
        assertThat(result.get("merchant.example:tee").inventoryItemName()).isEqualTo("Owned cotton tee");
    }

    @Test
    void explainDropsGeneratedExplanationsWithInternalProcessLanguage() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        FakeUserProductSearchPersistenceService persistenceService = new FakeUserProductSearchPersistenceService();
        UserProductRecommendationExplanationService service = service(openRouterChatClient, persistenceService);
        openRouterChatClient.response = """
                {
                  "products": [
                    {
                      "productKey": "merchant.example:tee",
                      "whyMeantForYou": "Curator confirmed organic cotton from catalog data.",
                      "matchedFilterIds": ["organic-cotton"],
                      "missedFilterIds": []
                    }
                  ]
                }
                """;

        Map<String, UserProductRecommendationExplanationResult> result = service.explain(
                UUID.randomUUID(),
                "cotton tee",
                "cotton tee",
                "profile-hash",
                settings(),
                List.of(snapshot())
        );

        assertThat(persistenceService.saved).isEmpty();
        assertThat(result.get("merchant.example:tee").whyMeantForYou())
                .isEqualTo("This matches your search based on available product details.");
    }

    @Test
    void explainUsesCachedInventoryRelationshipWithoutCallingOpenRouter() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        FakeUserProductSearchPersistenceService persistenceService = new FakeUserProductSearchPersistenceService();
        UUID inventoryItemId = UUID.randomUUID();
        UserProductRecommendationExplanationResult cached = new UserProductRecommendationExplanationResult(
                "merchant.example:tee",
                "product-hash",
                "Cached explanation",
                List.of("organic-cotton"),
                List.of(),
                UserInventoryRecommendationRelationship.COMPLEMENT,
                inventoryItemId,
                "Merino sweater"
        );
        persistenceService.cached.put(cached.productKey(), cached);
        UserProductRecommendationExplanationService service = service(openRouterChatClient, persistenceService);

        Map<String, UserProductRecommendationExplanationResult> result = service.explain(
                UUID.randomUUID(),
                "cotton tee",
                "cotton tee",
                "profile-hash",
                settings(),
                List.of(snapshot())
        );

        assertThat(openRouterChatClient.called).isFalse();
        assertThat(result.get("merchant.example:tee")).isEqualTo(cached);
    }

    @Test
    void explainKeepsProductsMissingFromGeneratedExplanationsWithFallback() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        FakeUserProductSearchPersistenceService persistenceService = new FakeUserProductSearchPersistenceService();
        UserProductRecommendationExplanationService service = service(openRouterChatClient, persistenceService);
        openRouterChatClient.response = """
                {
                  "products": [
                    {
                      "productKey": "merchant.example:tee",
                      "whyMeantForYou": "Organic cotton matches your profile.",
                      "matchedFilterIds": ["organic-cotton"],
                      "missedFilterIds": []
                    }
                  ]
                }
                """;

        Map<String, UserProductRecommendationExplanationResult> result = service.explain(
                UUID.randomUUID(),
                "cotton tee",
                "cotton tee",
                "profile-hash",
                settings(),
                List.of(
                        snapshot(),
                        snapshot("merchant.example:socks", "product-hash-socks", "socks", "Organic Cotton Socks")
                )
        );

        assertThat(result).containsOnlyKeys("merchant.example:tee", "merchant.example:socks");
        assertThat(result.get("merchant.example:socks"))
                .satisfies(explanation -> {
                    assertThat(explanation.productHash()).isEqualTo("product-hash-socks");
                    assertThat(explanation.whyMeantForYou())
                            .isEqualTo("This matches your search based on available product details.");
                    assertThat(explanation.matchedFilterIds()).isEmpty();
                    assertThat(explanation.missedFilterIds()).isEmpty();
                });
        assertThat(persistenceService.saved).singleElement()
                .extracting(UserProductRecommendationExplanationResult::productKey)
                .isEqualTo("merchant.example:tee");
    }

    @Test
    void explainReturnsCachedExplanationsWhenGenerationFails() {
        FakeOpenRouterChatClient openRouterChatClient = new FakeOpenRouterChatClient();
        openRouterChatClient.fail = true;
        FakeUserProductSearchPersistenceService persistenceService = new FakeUserProductSearchPersistenceService();
        UserProductRecommendationExplanationResult cached = new UserProductRecommendationExplanationResult(
                "merchant.example:tee",
                "product-hash",
                "Cached explanation",
                List.of("organic-cotton"),
                List.of()
        );
        persistenceService.cached.put(cached.productKey(), cached);
        UserProductRecommendationExplanationService service = service(openRouterChatClient, persistenceService);

        Map<String, UserProductRecommendationExplanationResult> result = service.explain(
                UUID.randomUUID(),
                "cotton tee",
                "cotton tee",
                "profile-hash",
                settings(),
                List.of(
                        snapshot(),
                        snapshot("merchant.example:socks", "product-hash-socks", "socks", "Organic Cotton Socks")
                )
        );

        assertThat(openRouterChatClient.called).isTrue();
        assertThat(result).containsOnlyKeys("merchant.example:tee", "merchant.example:socks");
        assertThat(result.get("merchant.example:tee")).isEqualTo(cached);
        assertThat(result.get("merchant.example:socks").whyMeantForYou())
                .isEqualTo("This matches your search based on available product details.");
        assertThat(persistenceService.saved).isEmpty();
    }

    private void assertProviderSafeSchema(OpenRouterJsonSchemaDefinition schema) {
        OpenRouterJsonSchemaDefinition products = schema.properties().get("products");
        assertThat(products.minItems()).isNull();
        assertThat(products.maxItems()).isNull();

        OpenRouterJsonSchemaDefinition product = products.items();
        assertThat(product.properties().get("productKey").enumValues()).isNull();
        assertThat(product.properties().get("matchedFilterIds").items().enumValues()).isNull();
        assertThat(product.properties().get("missedFilterIds").items().enumValues()).isNull();
    }

    private UserProductRecommendationExplanationService service(
            FakeOpenRouterChatClient openRouterChatClient,
            FakeUserProductSearchPersistenceService persistenceService
    ) {
        return new UserProductRecommendationExplanationService(
                openRouterChatClient,
                openRouterProperties(),
                new UserProductSearchProperties(
                        "v1",
                        "v1",
                        "v1",
                        4096,
                        Duration.ofHours(24),
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(30),
                        100,
                        Duration.ofSeconds(45),
                        128,
                        5,
                        12,
                        Duration.ofHours(24),
                        Duration.ofDays(7),
                        6,
                        3,
                        80),
                persistenceService,
                new ObjectMapper()
        );
    }

    private OpenRouterProperties openRouterProperties() {
        return new OpenRouterProperties(
                "https://openrouter.test/api/v1",
                "test-key",
                "Meant",
                new OpenRouterProperties.Models(
                        "preference-model",
                        "query-model",
                        "google/gemini-2.5-flash-lite",
                        "openrouter/free"
                )
        );
    }

    private UserSettingsResult settings() {
        ShoppingFilterResult organicCotton = new ShoppingFilterResult(
                "organic-cotton",
                "Organic cotton",
                "Prefer certified organic cotton.",
                "materials",
                "prefer",
                10
        );
        ShoppingFilterResult noPolyester = new ShoppingFilterResult(
                "no-polyester",
                "No polyester",
                "Avoid polyester.",
                "materials",
                "avoid",
                20
        );
        ShoppingFilterResult crypto = new ShoppingFilterResult(
                "crypto",
                "Crypto",
                "Prefer tasteful crypto references when relevant.",
                "interests",
                "prefer",
                30
        );
        return new UserSettingsResult(
                100,
                null,
                null,
                List.of(),
                List.of(organicCotton, noPolyester, crypto),
                List.of(organicCotton, noPolyester, crypto),
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private UserProductSearchProductSnapshot snapshot() {
        return snapshot("merchant.example:tee", "product-hash", "tee", "Organic Cotton Tee");
    }

    private UserProductSearchProductSnapshot snapshot(
            String productKey,
            String productHash,
            String productId,
            String title
    ) {
        MerchantSemanticProductResult product = new MerchantSemanticProductResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://merchant.example/mcp",
                1,
                0.9d,
                0.8d,
                productId,
                title,
                "<p>Organic cotton tee with no polyester.</p>",
                "https://merchant.example/products/" + productId,
                "https://merchant.example/" + productId + ".jpg",
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
        return new UserProductSearchProductSnapshot(
                productKey,
                productHash,
                product
        );
    }

    static class FakeOpenRouterChatClient extends OpenRouterChatClient {

        private String response;
        private boolean fail;
        private boolean called;
        private String model;
        private String systemPrompt;
        private String userPrompt;
        private OpenRouterJsonSchemaDefinition schema;

        FakeOpenRouterChatClient() {
            super(RestClient.builder(), new OpenRouterProperties(
                    "https://openrouter.test/api/v1",
                    "test-key",
                    "Meant",
                    new OpenRouterProperties.Models("test-model", "test-model", "test-model", "test-model")));
        }

        @Override
        public String completeJson(
                String model,
                String systemPrompt,
                String userPrompt,
                String schemaName,
                OpenRouterJsonSchemaDefinition schema
        ) {
            called = true;
            this.model = model;
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            this.schema = schema;
            if (fail) {
                throw new com.meant.api.common.exception.OpenRouterException("failed");
            }
            return response;
        }
    }

    static class FakeUserProductSearchPersistenceService extends UserProductSearchPersistenceService {

        private final Map<String, UserProductRecommendationExplanationResult> cached = new LinkedHashMap<>();
        private List<UserProductRecommendationExplanationResult> saved = List.of();

        FakeUserProductSearchPersistenceService() {
            super(
                    null,
                    null,
                    null,
                    null,
                    new UserTasteRankingService(),
                    new UserProductSearchCurationPolicy(),
                    new ObjectMapper(),
                    org.mockito.Mockito.mock(UserProductSearchCachePolicy.class)
            );
        }

        @Override
        public Map<String, UserProductRecommendationExplanationResult> findExplanations(
                UUID userId,
                String normalizedQuery,
                String profileHash,
                String model,
                String promptVersion,
                java.util.Collection<UserProductSearchProductSnapshot> products
        ) {
            return cached;
        }

        @Override
        public Map<String, UserProductRecommendationExplanationResult> saveExplanations(
                UUID userId,
                String normalizedQuery,
                String profileHash,
                String model,
                String promptVersion,
                List<UserProductRecommendationExplanationResult> explanations,
                Instant now
        ) {
            saved = explanations;
            return explanations.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            UserProductRecommendationExplanationResult::productKey,
                            explanation -> explanation,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
        }
    }
}
