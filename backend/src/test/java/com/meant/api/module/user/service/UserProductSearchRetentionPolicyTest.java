package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.entity.UserProductSearch;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.repository.UserProductRecommendationExplanationRepository;
import com.meant.api.module.user.repository.UserProductRecommendationFilterMatchRepository;
import com.meant.api.module.user.repository.UserProductSearchRepository;
import com.meant.api.module.user.repository.UserProductSearchResultItemRepository;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.service.CatalogDataUsePolicyMetrics;
import com.meant.api.plugin.catalog.common.service.CatalogDataUsePolicyResolver;
import com.meant.api.plugin.catalog.common.service.GenericUcpCatalogDataUsePolicy;
import com.meant.api.plugin.catalog.common.service.GenericUcpCatalogDataUseProperties;
import com.meant.api.plugin.catalog.shopify.ShopifyCatalogDataUsePolicy;
import com.meant.api.plugin.catalog.shopify.ShopifyCatalogDataUseProperties;
import com.meant.api.plugin.catalog.shopify.ShopifyGlobalCatalogNormalizer;
import com.meant.api.plugin.catalog.shopify.ShopifyGlobalCatalogProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchRetentionPolicyTest {
    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    private UserProductSearchRepository searches;
    private UserProductSearchResultItemRepository items;
    private UserProductSearchPersistenceService service;

    @BeforeEach
    void setUp() {
        searches = mock(UserProductSearchRepository.class);
        items = mock(UserProductSearchResultItemRepository.class);
        UserTasteRankingService ranking = mock(UserTasteRankingService.class);
        UserProductSearchCurationPolicy curation = mock(UserProductSearchCurationPolicy.class);
        when(ranking.rank(anyList(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(curation.visibleProducts(anyList(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ShopifyGlobalCatalogProperties shopifyProperties = mock(ShopifyGlobalCatalogProperties.class);
        when(shopifyProperties.sourceIdentity()).thenReturn("SHOPIFY_GLOBAL_CATALOG");
        CatalogDataUsePolicyResolver resolver = new CatalogDataUsePolicyResolver(
                List.of(
                        new GenericUcpCatalogDataUsePolicy(new GenericUcpCatalogDataUseProperties(
                                Duration.ofHours(6), Duration.ofMinutes(2), Duration.ofDays(30))),
                        new ShopifyCatalogDataUsePolicy(
                                shopifyProperties,
                                new ShopifyCatalogDataUseProperties(
                                        false,
                                        Duration.ofMinutes(15),
                                        Duration.ofMinutes(2),
                                        Duration.ofDays(30)
                                )
                        )
                ),
                new CatalogDataUsePolicyMetrics(registry)
        );
        service = new UserProductSearchPersistenceService(
                searches,
                items,
                mock(UserProductRecommendationExplanationRepository.class),
                mock(UserProductRecommendationFilterMatchRepository.class),
                ranking,
                curation,
                new ObjectMapper(),
                resolver
        );
    }

    @Test
    void shopifyCandidateCannotEnterGenericCacheAloneOrMixedAndMediaIsNeverStored() {
        service.saveSearch(USER_ID, "boots", "boots", "profile", "v1", NOW, NOW.plus(Duration.ofHours(24)),
                List.of(snapshot("shopify", shopifySource(), "https://cdn.shopify.test/boot.jpg")),
                Map.of(), null, null, false, 0, 10);
        service.saveSearch(USER_ID, "boots", "boots", "profile", "v1", NOW, NOW.plus(Duration.ofHours(24)),
                List.of(
                        snapshot("generic", GenericUcpCatalogDataUsePolicy.SOURCE, "https://merchant.test/boot.jpg"),
                        snapshot("shopify", shopifySource(), "https://cdn.shopify.test/boot.jpg")
                ), Map.of(), null, null, false, 0, 10);
        service.saveSearch(
                USER_ID,
                "boots",
                "boots",
                "profile",
                "v1",
                NOW,
                NOW.plus(Duration.ofHours(24)),
                List.of(snapshot("shopify", shopifySource(), "https://cdn.shopify.test/boot.jpg")),
                List.of(GenericUcpCatalogDataUsePolicy.SOURCE),
                Map.of(),
                null,
                null,
                false,
                0,
                10
        );

        verifyNoInteractions(searches, items);
    }

    @Test
    void approvedGenericPathPersistsAtomicallyWithSourceSpecificTtl() {
        when(searches.findByUserIdAndNormalizedQueryAndProfileHashAndSearchVersion(
                USER_ID, "boots", "profile", "v1")).thenReturn(Optional.empty());
        when(searches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveSearch(USER_ID, "boots", "boots", "profile", "v1", NOW, NOW.plus(Duration.ofHours(24)),
                List.of(snapshot("generic", GenericUcpCatalogDataUsePolicy.SOURCE, "https://merchant.test/boot.jpg")),
                Map.of(), null, null, false, 0, 10);

        var searchCaptor = org.mockito.ArgumentCaptor.forClass(UserProductSearch.class);
        verify(searches).save(searchCaptor.capture());
        assertThat(searchCaptor.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(6)));
        assertThat(searchCaptor.getValue().getRetentionPolicyFingerprint()).hasSize(64);
        verify(items).saveAll(anyList());
    }

    @Test
    void approvedGenericSourceCanCacheAnEmptyResultWithoutInventingAnUnknownSource() {
        when(searches.findByUserIdAndNormalizedQueryAndProfileHashAndSearchVersion(
                USER_ID, "boots", "profile", "v1")).thenReturn(Optional.empty());
        when(searches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveSearch(
                USER_ID,
                "boots",
                "boots",
                "profile",
                "v1",
                NOW,
                NOW.plus(Duration.ofHours(24)),
                List.of(),
                List.of(GenericUcpCatalogDataUsePolicy.SOURCE),
                Map.of(),
                null,
                null,
                false,
                0,
                10
        );

        verify(searches).save(any());
        verify(items).saveAll(List.of());
    }

    @Test
    void historicalCacheWithoutPolicyFingerprintInvalidatesSafely() {
        UserProductSearch historical = UserProductSearch.create(
                USER_ID, "boots", "boots", "profile", "v1", NOW, NOW.plusSeconds(3600), false);
        when(searches.findFirstByUserIdAndNormalizedQueryAndProfileHashAndSearchVersionAndExpiresAtAfterOrderByUpdatedAtDesc(
                USER_ID, "boots", "profile", "v1", NOW)).thenReturn(Optional.of(historical));
        MerchantSemanticProductResult historicalProduct = product("generic", null);
        UserProductSearchResultItem historicalItem = UserProductSearchResultItem.from(
                historical.getId(), "generic", "hash", historicalProduct, NOW);
        when(items.findBySearchIdOrderByRankAsc(historical.getId())).thenReturn(List.of(historicalItem));

        assertThat(service.findCachedSearch(
                USER_ID, "boots", "boots", "profile", "v1", "model", "prompt",
                null, null, NOW, 0, 10)).isEmpty();
        verify(items, never()).saveAll(anyList());
    }

    private UserProductSearchProductSnapshot snapshot(
            String key,
            DiscoverySourceIdentity source,
            String imageUrl
    ) {
        return new UserProductSearchProductSnapshot(key, "hash-" + key, product(key, imageUrl), source);
    }

    private MerchantSemanticProductResult product(String key, String imageUrl) {
        MerchantSemanticProductResult product = mock(MerchantSemanticProductResult.class);
        when(product.merchantId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        when(product.merchantDomain()).thenReturn("merchant.test");
        when(product.productId()).thenReturn("product-" + key);
        when(product.title()).thenReturn("Boot " + key);
        when(product.imageUrl()).thenReturn(imageUrl);
        when(product.rank()).thenReturn(1);
        return product;
    }

    private DiscoverySourceIdentity shopifySource() {
        return new DiscoverySourceIdentity(
                ShopifyGlobalCatalogNormalizer.SHOPIFY,
                ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL_CATALOG"
        );
    }
}
