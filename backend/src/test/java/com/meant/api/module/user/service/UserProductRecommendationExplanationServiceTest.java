package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.common.service.OpenRouterChatClient;
import com.meant.api.common.service.dto.OpenRouterJsonSchemaDefinition;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
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
                      "missedFilterIds": ["no-polyester"]
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
        assertThat(openRouterChatClient.schema.properties()).containsKey("products");
        assertThat(persistenceService.saved).hasSize(1);
        assertThat(result.get("merchant.example:tee").whyMeantForYou())
                .isEqualTo("Organic cotton and no polyester match your profile.");
        assertThat(result.get("merchant.example:tee").matchedFilterIds())
                .containsExactly("organic-cotton");
        assertThat(result.get("merchant.example:tee").missedFilterIds())
                .containsExactly("no-polyester");
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

    private UserProductRecommendationExplanationService service(
            FakeOpenRouterChatClient openRouterChatClient,
            FakeUserProductSearchPersistenceService persistenceService
    ) {
        return new UserProductRecommendationExplanationService(
                openRouterChatClient,
                openRouterProperties(),
                new UserProductSearchProperties("v1", "v1", "v1", Duration.ofHours(24)),
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
                        "google/gemini-2.5-flash-lite"
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
        return new UserSettingsResult(
                100,
                null,
                List.of(organicCotton, noPolyester),
                List.of(organicCotton, noPolyester),
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now()
        );
    }

    private UserProductSearchProductSnapshot snapshot() {
        MerchantSemanticProductResult product = new MerchantSemanticProductResult(
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
                "merchant.example:tee",
                "product-hash",
                product
        );
    }

    static class FakeOpenRouterChatClient extends OpenRouterChatClient {

        private String response;
        private boolean called;
        private String model;
        private OpenRouterJsonSchemaDefinition schema;

        FakeOpenRouterChatClient() {
            super(RestClient.builder(), new OpenRouterProperties(
                    "https://openrouter.test/api/v1",
                    "test-key",
                    "Meant",
                    new OpenRouterProperties.Models("test-model", "test-model", "test-model")));
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
            this.schema = schema;
            return response;
        }
    }

    static class FakeUserProductSearchPersistenceService extends UserProductSearchPersistenceService {

        private final Map<String, UserProductRecommendationExplanationResult> cached = new LinkedHashMap<>();
        private List<UserProductRecommendationExplanationResult> saved = List.of();

        FakeUserProductSearchPersistenceService() {
            super(null, null, null, null);
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
