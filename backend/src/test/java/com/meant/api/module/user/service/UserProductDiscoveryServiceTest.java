package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.user.constant.UserProductDiscoverySortDirection;
import com.meant.api.module.user.constant.UserProductDiscoverySortField;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.query.GetUserProductDiscoveryQuery;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProductDiscoveryServiceTest {

    @Test
    void getUsesFullSearchProfileHashForRecentProducts() {
        UUID userId = UUID.randomUUID();
        EnsureUserProfileCommand profileCommand =
                new EnsureUserProfileCommand(userId, "discovery@example.com", "Discovery", "User");
        UserSettingsResult settings = settings();
        UserProductSearchProperties searchProperties = searchProperties();
        OpenRouterProperties openRouterProperties = openRouterProperties();
        UserProductSearchHashService hashService = new UserProductSearchHashService(searchProperties);
        FakeUserSettingsService userSettingsService = new FakeUserSettingsService(settings);
        FakeUserSavedProductService userSavedProductService = new FakeUserSavedProductService();
        CapturingUserProductSearchPersistenceService persistenceService =
                new CapturingUserProductSearchPersistenceService();
        String inventoryProfileHash = "inventory:2:2026-06-18T10:00:00Z";
        String tasteProfileHash = "taste:profile";
        FakeUserInventoryService userInventoryService = new FakeUserInventoryService(inventoryProfileHash);
        FakeUserTasteProfileService userTasteProfileService = new FakeUserTasteProfileService(tasteProfileHash);
        UserProductDiscoveryService service = new UserProductDiscoveryService(
                userSettingsService,
                userSavedProductService,
                hashService,
                persistenceService,
                userInventoryService,
                userTasteProfileService,
                searchProperties,
                collectionProperties(),
                openRouterProperties
        );

        service.get(
                profileCommand,
                new GetUserProductDiscoveryQuery(
                        userId,
                        null,
                        UserProductDiscoverySortField.RECENT,
                        UserProductDiscoverySortDirection.DESC
                )
        );

        assertThat(persistenceService.profileHash)
                .isEqualTo(hashService.searchProfileHash(settings, inventoryProfileHash, tasteProfileHash));
        assertThat(persistenceService.userId).isEqualTo(userId);
        assertThat(persistenceService.searchVersion).isEqualTo(searchProperties.searchVersion());
        assertThat(persistenceService.model).isEqualTo(openRouterProperties.models().productRecommendationExplainer());
        assertThat(persistenceService.promptVersion).isEqualTo(searchProperties.explanationPromptVersion());
        assertThat(persistenceService.recentSearchLimit).isEqualTo(searchProperties.discoveryRecentSearchLimit());
        assertThat(persistenceService.productLimit).isEqualTo(searchProperties.discoveryRecentProductLimit());
    }

    private UserSettingsResult settings() {
        UserLocationResult location = new UserLocationResult("United States", "US", "New York");
        return new UserSettingsResult(
                120,
                "men",
                location,
                List.of(location),
                List.of(new ShoppingFilterResult(
                        "organic",
                        "Organic",
                        "Prefer organic materials.",
                        "materials",
                        "prefer",
                        10
                )),
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-06-17T10:00:00Z"),
                Instant.parse("2026-06-17T10:00:00Z")
        );
    }

    private UserProductSearchProperties searchProperties() {
        return new UserProductSearchProperties(
                "v1",
                "query-v1",
                "prompt-v1",
                Duration.ofHours(24),
                Duration.ofSeconds(45),
                128,
                5,
                12,
                Duration.ofHours(24),
                Duration.ofDays(7),
                6,
                3,
                80
        );
    }

    private UserCollectionProperties collectionProperties() {
        return new UserCollectionProperties(
                new UserCollectionProperties.SavedProducts(20, 100, 200, 50, 20),
                new UserCollectionProperties.Inventory(20, 100, 200)
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
                        "explainer-model",
                        "chat-model"
                )
        );
    }

    private static class FakeUserSettingsService extends UserSettingsService {

        private final UserSettingsResult settings;

        FakeUserSettingsService(UserSettingsResult settings) {
            super(null, null, null, null, null);
            this.settings = settings;
        }

        @Override
        public UserSettingsResult get(EnsureUserProfileCommand profileCommand) {
            return settings;
        }
    }

    private static class FakeUserSavedProductService extends UserSavedProductService {

        FakeUserSavedProductService() {
            super(
                    org.mockito.Mockito.mock(UserService.class),
                    org.mockito.Mockito.mock(UserSettingsService.class),
                    org.mockito.Mockito.mock(com.meant.api.module.user.repository.UserSavedProductRepository.class),
                    org.mockito.Mockito.mock(UserCollectionProperties.class),
                    org.mockito.Mockito.mock(UserSavedProductReferenceResolver.class),
                    org.mockito.Mockito.mock(com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver.class),
                    org.mockito.Mockito.mock(com.meant.api.module.catalog.service.CatalogProductRehydrationService.class),
                    org.mockito.Mockito.mock(UserSavedProductPersistenceService.class),
                    org.mockito.Mockito.mock(UserSavedProductResultMapper.class)
            );
        }

        @Override
        public List<com.meant.api.module.user.service.dto.UserSavedProductResult> list(
                EnsureUserProfileCommand profileCommand,
                ListSavedProductsQuery query
        ) {
            return List.of();
        }
    }

    private static class CapturingUserProductSearchPersistenceService extends UserProductSearchPersistenceService {

        private UUID userId;
        private String profileHash;
        private String searchVersion;
        private String model;
        private String promptVersion;
        private int recentSearchLimit;
        private int productLimit;

        CapturingUserProductSearchPersistenceService() {
            super(
                    org.mockito.Mockito.mock(com.meant.api.module.user.repository.UserProductSearchRepository.class),
                    org.mockito.Mockito.mock(com.meant.api.module.user.repository.UserProductSearchResultItemRepository.class),
                    org.mockito.Mockito.mock(com.meant.api.module.user.repository.UserProductRecommendationExplanationRepository.class),
                    org.mockito.Mockito.mock(com.meant.api.module.user.repository.UserProductRecommendationFilterMatchRepository.class),
                    org.mockito.Mockito.mock(UserTasteRankingService.class),
                    org.mockito.Mockito.mock(UserProductSearchCurationPolicy.class),
                    new tools.jackson.databind.ObjectMapper(),
                    org.mockito.Mockito.mock(UserProductSearchCachePolicy.class)
            );
        }

        @Override
        public List<UserProductSearchProductResult> findRecentProducts(
                UUID userId,
                String profileHash,
                String searchVersion,
                String model,
                String promptVersion,
                Instant now,
                int recentSearchLimit,
                int productLimit
        ) {
            this.userId = userId;
            this.profileHash = profileHash;
            this.searchVersion = searchVersion;
            this.model = model;
            this.promptVersion = promptVersion;
            this.recentSearchLimit = recentSearchLimit;
            this.productLimit = productLimit;
            return List.of();
        }
    }

    private static class FakeUserInventoryService extends UserInventoryService {

        private final String inventoryProfileHash;

        FakeUserInventoryService(String inventoryProfileHash) {
            super(null, null, null, null, null);
            this.inventoryProfileHash = inventoryProfileHash;
        }

        @Override
        public String inventoryProfileHash(UUID userId) {
            return inventoryProfileHash;
        }
    }

    private static class FakeUserTasteProfileService extends UserTasteProfileService {

        private final String tasteProfileHash;

        FakeUserTasteProfileService(String tasteProfileHash) {
            super(null, null, null, null);
            this.tasteProfileHash = tasteProfileHash;
        }

        @Override
        public UserTasteProfileResult profile(UUID userId, UserSettingsResult settings) {
            return new UserTasteProfileResult(tasteProfileHash, List.of(), List.of());
        }
    }
}
