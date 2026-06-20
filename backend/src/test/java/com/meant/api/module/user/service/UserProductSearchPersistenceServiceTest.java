package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.user.entity.UserProductRecommendationExplanation;
import com.meant.api.module.user.entity.UserProductRecommendationFilterMatch;
import com.meant.api.module.user.entity.UserProductSearch;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.repository.UserProductRecommendationExplanationRepository;
import com.meant.api.module.user.repository.UserProductRecommendationFilterMatchRepository;
import com.meant.api.module.user.repository.UserProductSearchRepository;
import com.meant.api.module.user.repository.UserProductSearchResultItemRepository;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchPersistenceServiceTest {

    private static final String QUERY = "organic cotton tee";
    private static final String NORMALIZED_QUERY = "organic cotton tee";
    private static final String PROFILE_HASH = "profile";
    private static final String SEARCH_VERSION = "v1";
    private static final String MODEL = "model";
    private static final String PROMPT_VERSION = "prompt";
    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    private boolean explanationsLoaded;
    private boolean matchesLoaded;

    @Test
    void saveSearchSkipsProductsWithoutExplanations() {
        List<UserProductSearchResultItem> savedItems = new ArrayList<>();
        UserProductSearchPersistenceService service = new UserProductSearchPersistenceService(
                searchRepository(),
                resultItemRepository(savedItems),
                unusedRepository(UserProductRecommendationExplanationRepository.class),
                unusedRepository(UserProductRecommendationFilterMatchRepository.class),
                new UserTasteRankingService(),
                new ObjectMapper()
        );
        UserProductRecommendationExplanationResult explanation = new UserProductRecommendationExplanationResult(
                "merchant.example:tee",
                "hash-tee",
                "Organic cotton matches your profile.",
                List.of("organic-cotton"),
                List.of()
        );

        UserProductSearchResult result = service.saveSearch(
                UUID.randomUUID(),
                "cotton basics",
                "cotton basics",
                "profile-hash",
                SEARCH_VERSION,
                NOW,
                NOW.plusSeconds(3600),
                List.of(
                        snapshot("merchant.example:tee", "hash-tee", "tee", "Organic Cotton Tee", 1),
                        snapshot("merchant.example:socks", "hash-socks", "socks", "Organic Cotton Socks", 2)
                ),
                Map.of(explanation.productKey(), explanation),
                null,
                null,
                false,
                0,
                20
        );

        assertThat(result.products()).singleElement()
                .satisfies(product -> {
                    assertThat(product.productKey()).isEqualTo("merchant.example:tee");
                    assertThat(product.whyMeantForYou()).isEqualTo("Organic cotton matches your profile.");
                    assertThat(product.listPriceAmount()).isEqualTo(4800L);
                    assertThat(product.ratingScore()).isEqualTo(4.7d);
                    assertThat(product.reviewCount()).isEqualTo(128);
                    assertThat(product.media()).extracting("type").containsExactly("image", "video");
                    assertThat(product.certifications()).containsExactly("GOTS");
                    assertThat(product.materials()).containsExactly("Organic cotton");
                });
        assertThat(savedItems).singleElement()
                .satisfies(item -> {
                    assertThat(item.getProductKey()).isEqualTo("merchant.example:tee");
                    assertThat(item.getListPriceAmount()).isEqualTo(4800L);
                    assertThat(item.getRatingScore()).isEqualTo(4.7d);
                    assertThat(item.getMediaJson()).contains("video");
                    assertThat(item.getCertificationsJson()).contains("GOTS");
                });
    }

    @Test
    void saveSearchDeduplicatesProductsByProductKey() {
        List<UserProductSearchResultItem> savedItems = new ArrayList<>();
        UserProductSearchPersistenceService service = new UserProductSearchPersistenceService(
                searchRepository(),
                resultItemRepository(savedItems),
                unusedRepository(UserProductRecommendationExplanationRepository.class),
                unusedRepository(UserProductRecommendationFilterMatchRepository.class),
                new UserTasteRankingService(),
                new ObjectMapper()
        );
        UserProductRecommendationExplanationResult teeExplanation = new UserProductRecommendationExplanationResult(
                "merchant.example:tee",
                "hash-tee",
                "Organic cotton matches your profile.",
                List.of("organic-cotton"),
                List.of()
        );
        UserProductRecommendationExplanationResult hatExplanation = new UserProductRecommendationExplanationResult(
                "merchant.example:hat",
                "hash-hat",
                "Natural fiber accessories match your profile.",
                List.of("natural-fibers"),
                List.of()
        );

        UserProductSearchResult result = service.saveSearch(
                UUID.randomUUID(),
                "cotton basics",
                "cotton basics",
                "profile-hash",
                SEARCH_VERSION,
                NOW,
                NOW.plusSeconds(3600),
                List.of(
                        snapshot("merchant.example:tee", "hash-tee", "tee", "Organic Cotton Tee", 1),
                        snapshot("merchant.example:tee", "hash-tee", "tee", "Organic Cotton Tee Duplicate", 2),
                        snapshot("merchant.example:hat", "hash-hat", "hat", "Organic Cotton Hat", 3)
                ),
                Map.of(
                        teeExplanation.productKey(), teeExplanation,
                        hatExplanation.productKey(), hatExplanation
                ),
                null,
                null,
                false,
                0,
                20
        );

        assertThat(savedItems)
                .extracting(UserProductSearchResultItem::getProductKey)
                .containsExactly("merchant.example:tee", "merchant.example:hat");
        assertThat(result.products())
                .extracting("productKey")
                .containsExactly("merchant.example:tee", "merchant.example:hat");
    }

    @Test
    void findCachedSearchReturnsRequestedPageWithNextOffset() {
        UUID userId = UUID.randomUUID();
        UserProductSearch search = search(userId, true);
        List<UserProductSearchResultItem> items = items(search.getId(), 21);
        UserProductSearchPersistenceService service = service(userId, search, items);

        Optional<UserProductSearchResult> result = service.findCachedSearch(
                userId,
                QUERY,
                NORMALIZED_QUERY,
                PROFILE_HASH,
                SEARCH_VERSION,
                MODEL,
                PROMPT_VERSION,
                null,
                null,
                NOW,
                0,
                20
        );

        assertThat(result).isPresent();
        assertThat(result.get().cached()).isTrue();
        assertThat(result.get().offset()).isZero();
        assertThat(result.get().limit()).isEqualTo(20);
        assertThat(result.get().nextOffset()).isEqualTo(20);
        assertThat(result.get().hasMore()).isTrue();
        assertThat(result.get().products()).hasSize(20);
        assertThat(result.get().products().getFirst().productKey()).isEqualTo("merchant.example:item-1");
        assertThat(result.get().products().getLast().productKey()).isEqualTo("merchant.example:item-20");
    }

    @Test
    void findCachedSearchTreatsStoredJsonNullAsEmptyRichCatalogData() {
        UUID userId = UUID.randomUUID();
        UserProductSearch search = search(userId, false);
        UserProductSearchResultItem item = UserProductSearchResultItem.from(
                search.getId(),
                "merchant.example:item-1",
                "hash-1",
                product(1),
                NOW,
                new UserProductSearchResultItem.RichCatalogSnapshot(
                        "null",
                        "null",
                        "null",
                        "null",
                        "null",
                        "null",
                        "null"
                )
        );
        UserProductSearchPersistenceService service = service(userId, search, List.of(item));

        Optional<UserProductSearchResult> result = service.findCachedSearch(
                userId,
                QUERY,
                NORMALIZED_QUERY,
                PROFILE_HASH,
                SEARCH_VERSION,
                MODEL,
                PROMPT_VERSION,
                null,
                null,
                NOW,
                0,
                20
        );

        assertThat(result).isPresent();
        assertThat(result.get().products()).singleElement()
                .satisfies(product -> {
                    assertThat(product.media()).isEmpty();
                    assertThat(product.categories()).isEmpty();
                    assertThat(product.certifications()).isEmpty();
                    assertThat(product.materials()).isEmpty();
                    assertThat(product.skus()).isEmpty();
                    assertThat(product.collections()).isEmpty();
                    assertThat(product.attributes()).isEmpty();
                });
    }

    @Test
    void findCachedSearchReturnsEmptyWhenWindowIsTooSmallAndMoreMayExist() {
        UUID userId = UUID.randomUUID();
        UserProductSearch search = search(userId, true);
        List<UserProductSearchResultItem> items = items(search.getId(), 21);
        UserProductSearchPersistenceService service = service(userId, search, items);

        Optional<UserProductSearchResult> result = service.findCachedSearch(
                userId,
                QUERY,
                NORMALIZED_QUERY,
                PROFILE_HASH,
                SEARCH_VERSION,
                MODEL,
                PROMPT_VERSION,
                null,
                null,
                NOW,
                20,
                20
        );

        assertThat(result).isEmpty();
        assertThat(explanationsLoaded).isFalse();
        assertThat(matchesLoaded).isFalse();
    }

    @Test
    void findCachedSearchServesPartialFinalPageWhenSearchIsExhausted() {
        UUID userId = UUID.randomUUID();
        UserProductSearch search = search(userId, false);
        List<UserProductSearchResultItem> items = items(search.getId(), 21);
        UserProductSearchPersistenceService service = service(userId, search, items);

        Optional<UserProductSearchResult> result = service.findCachedSearch(
                userId,
                QUERY,
                NORMALIZED_QUERY,
                PROFILE_HASH,
                SEARCH_VERSION,
                MODEL,
                PROMPT_VERSION,
                null,
                null,
                NOW,
                20,
                20
        );

        assertThat(result).isPresent();
        assertThat(result.get().nextOffset()).isNull();
        assertThat(result.get().hasMore()).isFalse();
        assertThat(result.get().products()).singleElement()
                .satisfies(product -> assertThat(product.productKey()).isEqualTo("merchant.example:item-21"));
    }

    private UserProductSearchPersistenceService service(
            UUID userId,
            UserProductSearch search,
            List<UserProductSearchResultItem> items
    ) {
        explanationsLoaded = false;
        matchesLoaded = false;
        return new UserProductSearchPersistenceService(
                cachedSearchRepository(search),
                cachedResultItemRepository(search.getId(), items),
                explanationRepository(userId, items),
                filterMatchRepository(),
                new UserTasteRankingService(),
                new ObjectMapper()
        );
    }

    private UserProductSearchRepository searchRepository() {
        return repository(UserProductSearchRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findByUserIdAndNormalizedQueryAndProfileHashAndSearchVersion" -> Optional.empty();
            case "save" -> args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private UserProductSearchRepository cachedSearchRepository(UserProductSearch search) {
        return repository(UserProductSearchRepository.class, (proxy, method, args) -> {
            if ("findFirstByUserIdAndNormalizedQueryAndProfileHashAndSearchVersionAndExpiresAtAfterOrderByUpdatedAtDesc"
                    .equals(method.getName())) {
                return Optional.of(search);
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private UserProductSearchResultItemRepository resultItemRepository(
            List<UserProductSearchResultItem> savedItems
    ) {
        return repository(UserProductSearchResultItemRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "deleteBySearchId" -> null;
            case "saveAll" -> {
                savedItems.clear();
                Iterable<UserProductSearchResultItem> items = resultItems(args[0]);
                StreamSupport.stream(items.spliterator(), false)
                        .forEach(savedItems::add);
                yield savedItems;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private UserProductSearchResultItemRepository cachedResultItemRepository(
            UUID searchId,
            List<UserProductSearchResultItem> items
    ) {
        return repository(UserProductSearchResultItemRepository.class, (proxy, method, args) -> {
            if ("findBySearchIdOrderByRankAsc".equals(method.getName())) {
                return items.stream()
                        .filter(item -> item.getSearchId().equals(searchId))
                        .toList();
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private UserProductRecommendationExplanationRepository explanationRepository(
            UUID userId,
            List<UserProductSearchResultItem> items
    ) {
        return repository(UserProductRecommendationExplanationRepository.class, (proxy, method, args) -> {
            if ("findByUserIdAndNormalizedQueryAndProfileHashAndModelAndPromptVersionAndProductKeyIn"
                    .equals(method.getName())) {
                explanationsLoaded = true;
                return items.stream()
                        .map(item -> UserProductRecommendationExplanation.create(
                                userId,
                                NORMALIZED_QUERY,
                                PROFILE_HASH,
                                item.getProductKey(),
                                item.getProductHash(),
                                MODEL,
                                PROMPT_VERSION,
                                "Matches your profile.",
                                NOW
                        ))
                        .toList();
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private UserProductRecommendationFilterMatchRepository filterMatchRepository() {
        return repository(UserProductRecommendationFilterMatchRepository.class, (proxy, method, args) -> {
            if ("findByExplanationIdInOrderByRankAsc".equals(method.getName())) {
                matchesLoaded = true;
                return List.<UserProductRecommendationFilterMatch>of();
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private Iterable<UserProductSearchResultItem> resultItems(Object value) {
        return (Iterable<UserProductSearchResultItem>) value;
    }

    private <T> T unusedRepository(Class<T> type) {
        return repository(type, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private <T> T repository(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                objectAwareHandler(handler)
        ));
    }

    private InvocationHandler objectAwareHandler(InvocationHandler handler) {
        return (proxy, method, args) -> {
            if (method.getDeclaringClass().equals(Object.class)) {
                return objectMethod(proxy, method, args);
            }
            return handler.invoke(proxy, method, args);
        };
    }

    private Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "FakeRepository{" + proxy.getClass().getInterfaces()[0].getSimpleName() + "}";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private UserProductSearch search(UUID userId, boolean hasMoreProducts) {
        return UserProductSearch.create(
                userId,
                QUERY,
                NORMALIZED_QUERY,
                PROFILE_HASH,
                SEARCH_VERSION,
                NOW,
                NOW.plusSeconds(3600),
                hasMoreProducts
        );
    }

    private List<UserProductSearchResultItem> items(UUID searchId, int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> UserProductSearchResultItem.from(
                        searchId,
                        "merchant.example:item-" + index,
                        "hash-" + index,
                        product(index),
                        NOW
                ))
                .toList();
    }

    private UserProductSearchProductSnapshot snapshot(
            String productKey,
            String productHash,
            String productId,
            String title,
            int rank
    ) {
        return new UserProductSearchProductSnapshot(productKey, productHash, product(productId, title, rank));
    }

    private MerchantSemanticProductResult product(int index) {
        return product(
                UUID.nameUUIDFromBytes(("merchant-" + index).getBytes(StandardCharsets.UTF_8)),
                "item-" + index,
                "Item " + index,
                index
        );
    }

    private MerchantSemanticProductResult product(String productId, String title, int rank) {
        return product(UUID.randomUUID(), productId, title, rank);
    }

    private MerchantSemanticProductResult product(UUID merchantId, String productId, String title, int rank) {
        return new MerchantSemanticProductResult(
                merchantId,
                "merchant.example",
                "Merchant",
                "https://merchant.example/mcp",
                1,
                0.9d,
                0.8d,
                productId,
                title,
                "<p>Organic cotton.</p>",
                "https://merchant.example/products/" + productId,
                "https://merchant.example/" + productId + ".jpg",
                3800L,
                3800L,
                "USD",
                4800L,
                "USD",
                4.7d,
                128,
                List.of(
                        new ProductCatalogMedia("image", "https://merchant.example/" + productId + ".jpg", title),
                        new ProductCatalogMedia("video", "https://merchant.example/" + productId + ".mp4", null)
                ),
                List.of(new ProductCatalogCategory("Apparel", "shopify")),
                List.of("GOTS"),
                List.of("Organic cotton"),
                List.of("SKU-" + productId),
                List.of("Basics"),
                List.of(new ProductCatalogAttribute("fabric", "100% organic cotton")),
                true,
                null,
                "Organic cotton.",
                "https://merchant.example/" + productId + ".jpg",
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
                "https://merchant.example/" + productId + ".jpg",
                title,
                true,
                rank,
                0.92d,
                rank
        );
    }
}
