package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.properties.GenericUcpCatalogDataUseProperties;
import com.meant.api.module.merchant.service.GenericUcpCatalogDataUsePolicy;
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
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyMetrics;
import com.meant.api.module.catalog.service.CatalogDataUsePolicyResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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
    private int explanationsLoadCount;
    private int matchesLoadCount;
    private int recentResultItemsLoadCount;
    private int explanationSaveBatchCount;
    private int filterMatchSaveBatchCount;

    @Test
    void saveSearchKeepsProductsWithoutExplanations() {
        List<UserProductSearchResultItem> savedItems = new ArrayList<>();
        UserProductSearchPersistenceService service = new UserProductSearchPersistenceService(
                searchRepository(),
                resultItemRepository(savedItems),
                unusedRepository(UserProductRecommendationExplanationRepository.class),
                unusedRepository(UserProductRecommendationFilterMatchRepository.class),
                new UserTasteRankingService(),
                new UserProductSearchCurationPolicy(),
                new ObjectMapper(),
                cachePolicy()
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

        assertThat(result.products()).hasSize(2);
        assertThat(result.products().getFirst())
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
        assertThat(result.products().get(1))
                .satisfies(product -> {
                    assertThat(product.productKey()).isEqualTo("merchant.example:socks");
                    assertThat(product.whyMeantForYou())
                            .isEqualTo("This matches your search based on available product details.");
                    assertThat(product.matchedFilterIds()).isEmpty();
                    assertThat(product.missedFilterIds()).isEmpty();
                });
        assertThat(savedItems)
                .extracting(UserProductSearchResultItem::getProductKey)
                .containsExactly("merchant.example:tee", "merchant.example:socks");
        assertThat(savedItems.getFirst())
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
                new UserProductSearchCurationPolicy(),
                new ObjectMapper(),
                cachePolicy()
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
    void findCachedSearchKeepsItemsWithoutStoredExplanations() {
        UUID userId = UUID.randomUUID();
        UserProductSearch search = search(userId, false);
        List<UserProductSearchResultItem> items = List.of(UserProductSearchResultItem.from(
                search.getId(),
                "merchant.example:item-1",
                "hash-1",
                product(1),
                NOW
        ));
        explanationsLoaded = false;
        matchesLoaded = false;
        UserProductSearchPersistenceService service = new UserProductSearchPersistenceService(
                cachedSearchRepository(search),
                cachedResultItemRepository(search.getId(), items),
                emptyExplanationRepository(),
                unusedRepository(UserProductRecommendationFilterMatchRepository.class),
                new UserTasteRankingService(),
                new UserProductSearchCurationPolicy(),
                new ObjectMapper(),
                cachePolicy()
        );

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
                    assertThat(product.productKey()).isEqualTo("merchant.example:item-1");
                    assertThat(product.whyMeantForYou())
                            .isEqualTo("This matches your search based on available product details.");
                });
        assertThat(explanationsLoaded).isTrue();
        assertThat(matchesLoaded).isFalse();
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
    void findCachedSearchReturnsEmptyWhenVisibleWindowIsTooSmallAndMoreMayExist() {
        UUID userId = UUID.randomUUID();
        UserProductSearch search = search(userId, true);
        List<UserProductSearchResultItem> items = new ArrayList<>(items(search.getId(), 19));
        items.addAll(items(search.getId(), 80, 100));
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
        assertThat(explanationsLoaded).isTrue();
        assertThat(matchesLoaded).isTrue();
    }

    @Test
    void findCachedSearchReturnsEmptyWhenLaterVisiblePageIsPartialAndMoreMayExist() {
        UUID userId = UUID.randomUUID();
        UserProductSearch search = search(userId, true);
        List<UserProductSearchResultItem> items = new ArrayList<>(items(search.getId(), 21));
        items.addAll(items(search.getId(), 80, 100));
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
        assertThat(explanationsLoaded).isTrue();
        assertThat(matchesLoaded).isTrue();
    }

    @Test
    void findCachedSearchServesLowYieldFirstPage() {
        UUID userId = UUID.randomUUID();
        UserProductSearch search = search(userId, true);
        List<UserProductSearchResultItem> items = new ArrayList<>(items(search.getId(), 5));
        items.addAll(items(search.getId(), 80, 95));
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
        assertThat(result.get().products())
                .extracting("productKey")
                .containsExactly(
                        "merchant.example:item-1",
                        "merchant.example:item-2",
                        "merchant.example:item-3",
                        "merchant.example:item-4",
                        "merchant.example:item-5"
                );
        assertThat(result.get().nextOffset()).isEqualTo(20);
        assertThat(result.get().hasMore()).isTrue();
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

    @Test
    void findRecentProductsLoadsSearchItemsAndExplanationsInBatches() {
        UUID userId = UUID.randomUUID();
        UserProductSearch firstSearch = search(userId, "cotton tees", "cotton tees", false);
        UserProductSearch secondSearch = search(userId, "linen hats", "linen hats", false);
        List<UserProductSearch> searches = List.of(firstSearch, secondSearch);
        List<UserProductSearchResultItem> items = List.of(
                item(firstSearch.getId(), 1),
                item(firstSearch.getId(), 2),
                item(secondSearch.getId(), 3)
        );
        explanationsLoadCount = 0;
        matchesLoadCount = 0;
        recentResultItemsLoadCount = 0;
        UserProductSearchPersistenceService service = new UserProductSearchPersistenceService(
                recentSearchRepository(searches),
                recentResultItemRepository(items),
                recentExplanationRepository(userId, searches, items),
                filterMatchRepository(),
                new UserTasteRankingService(),
                new UserProductSearchCurationPolicy(),
                new ObjectMapper(),
                cachePolicy()
        );

        List<UserProductSearchProductResult> result = service.findRecentProducts(
                userId,
                PROFILE_HASH,
                SEARCH_VERSION,
                MODEL,
                PROMPT_VERSION,
                NOW,
                5,
                3
        );

        assertThat(result)
                .extracting(UserProductSearchProductResult::productKey)
                .containsExactly(
                        "merchant.example:item-1",
                        "merchant.example:item-2",
                        "merchant.example:item-3"
                );
        assertThat(recentResultItemsLoadCount).isOne();
        assertThat(explanationsLoadCount).isOne();
        assertThat(matchesLoadCount).isOne();
    }

    @Test
    void saveExplanationsPersistsExplanationsAndFilterMatchesInBatches() {
        UUID userId = UUID.randomUUID();
        List<UserProductRecommendationExplanation> savedExplanations = new ArrayList<>();
        List<UserProductRecommendationFilterMatch> savedMatches = new ArrayList<>();
        explanationSaveBatchCount = 0;
        filterMatchSaveBatchCount = 0;
        UserProductSearchPersistenceService service = new UserProductSearchPersistenceService(
                unusedRepository(UserProductSearchRepository.class),
                unusedRepository(UserProductSearchResultItemRepository.class),
                savingExplanationRepository(savedExplanations),
                savingFilterMatchRepository(savedMatches),
                new UserTasteRankingService(),
                new UserProductSearchCurationPolicy(),
                new ObjectMapper(),
                cachePolicy()
        );

        Map<String, UserProductRecommendationExplanationResult> result = service.saveExplanations(
                userId,
                NORMALIZED_QUERY,
                PROFILE_HASH,
                MODEL,
                PROMPT_VERSION,
                List.of(
                        new UserProductRecommendationExplanationResult(
                                "merchant.example:item-1",
                                "hash-1",
                                "First product matches.",
                                List.of("organic", "local"),
                                List.of("vegan")
                        ),
                        new UserProductRecommendationExplanationResult(
                                "merchant.example:item-2",
                                "hash-2",
                                "Second product matches.",
                                List.of("durable"),
                                List.of()
                        )
                ),
                NOW
        );

        assertThat(savedExplanations)
                .extracting(UserProductRecommendationExplanation::getProductKey)
                .containsExactly("merchant.example:item-1", "merchant.example:item-2");
        assertThat(savedMatches)
                .extracting(UserProductRecommendationFilterMatch::getFilterId)
                .containsExactly("organic", "local", "vegan", "durable");
        assertThat(result.keySet()).containsExactly("merchant.example:item-1", "merchant.example:item-2");
        assertThat(result.get("merchant.example:item-1").matchedFilterIds()).containsExactly("organic", "local");
        assertThat(result.get("merchant.example:item-1").missedFilterIds()).containsExactly("vegan");
        assertThat(explanationSaveBatchCount).isOne();
        assertThat(filterMatchSaveBatchCount).isOne();
    }

    private UserProductSearchPersistenceService service(
            UUID userId,
            UserProductSearch search,
            List<UserProductSearchResultItem> items
    ) {
        explanationsLoaded = false;
        matchesLoaded = false;
        explanationsLoadCount = 0;
        matchesLoadCount = 0;
        return new UserProductSearchPersistenceService(
                cachedSearchRepository(search),
                cachedResultItemRepository(search.getId(), items),
                explanationRepository(userId, items),
                filterMatchRepository(),
                new UserTasteRankingService(),
                new UserProductSearchCurationPolicy(),
                new ObjectMapper(),
                cachePolicy()
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

    private UserProductSearchRepository recentSearchRepository(List<UserProductSearch> searches) {
        return repository(UserProductSearchRepository.class, (proxy, method, args) -> {
            if ("findByUserIdAndProfileHashAndSearchVersionAndExpiresAtAfterOrderByUpdatedAtDesc"
                    .equals(method.getName())) {
                return searches;
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

    private UserProductSearchResultItemRepository recentResultItemRepository(
            List<UserProductSearchResultItem> items
    ) {
        return repository(UserProductSearchResultItemRepository.class, (proxy, method, args) -> {
            if ("findBySearchIdIn".equals(method.getName())) {
                recentResultItemsLoadCount++;
                Collection<UUID> searchIds = searchIds(args[0]);
                return items.stream()
                        .filter(item -> searchIds.contains(item.getSearchId()))
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
                explanationsLoadCount++;
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

    private UserProductRecommendationExplanationRepository emptyExplanationRepository() {
        return repository(UserProductRecommendationExplanationRepository.class, (proxy, method, args) -> {
            if ("findByUserIdAndNormalizedQueryAndProfileHashAndModelAndPromptVersionAndProductKeyIn"
                    .equals(method.getName())) {
                explanationsLoaded = true;
                explanationsLoadCount++;
                return List.<UserProductRecommendationExplanation>of();
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private UserProductRecommendationExplanationRepository recentExplanationRepository(
            UUID userId,
            List<UserProductSearch> searches,
            List<UserProductSearchResultItem> items
    ) {
        Map<UUID, UserProductSearch> searchesById = searches.stream()
                .collect(LinkedHashMap::new,
                        (values, search) -> values.put(search.getId(), search),
                        LinkedHashMap::putAll);
        return repository(UserProductRecommendationExplanationRepository.class, (proxy, method, args) -> {
            if ("findByUserIdAndProfileHashAndModelAndPromptVersionAndProductKeyIn"
                    .equals(method.getName())) {
                explanationsLoaded = true;
                explanationsLoadCount++;
                Collection<String> productKeys = productKeys(args[4]);
                return items.stream()
                        .filter(item -> productKeys.contains(item.getProductKey()))
                        .map(item -> UserProductRecommendationExplanation.create(
                                userId,
                                searchesById.get(item.getSearchId()).getNormalizedQuery(),
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

    private UserProductRecommendationExplanationRepository savingExplanationRepository(
            List<UserProductRecommendationExplanation> savedExplanations
    ) {
        return repository(UserProductRecommendationExplanationRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "saveAll" -> {
                explanationSaveBatchCount++;
                savedExplanations.clear();
                StreamSupport.stream(explanationEntities(args[0]).spliterator(), false)
                        .forEach(savedExplanations::add);
                yield savedExplanations;
            }
            case "findByUserIdAndNormalizedQueryAndProfileHashAndModelAndPromptVersionAndProductKeyIn" -> {
                Collection<String> productKeys = productKeys(args[5]);
                yield savedExplanations.stream()
                        .filter(explanation -> productKeys.contains(explanation.getProductKey()))
                        .toList();
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private UserProductRecommendationFilterMatchRepository filterMatchRepository() {
        return repository(UserProductRecommendationFilterMatchRepository.class, (proxy, method, args) -> {
            if ("findByExplanationIdInOrderByRankAsc".equals(method.getName())) {
                matchesLoaded = true;
                matchesLoadCount++;
                return List.<UserProductRecommendationFilterMatch>of();
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private UserProductRecommendationFilterMatchRepository savingFilterMatchRepository(
            List<UserProductRecommendationFilterMatch> savedMatches
    ) {
        return repository(UserProductRecommendationFilterMatchRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "saveAll" -> {
                filterMatchSaveBatchCount++;
                savedMatches.clear();
                StreamSupport.stream(filterMatchEntities(args[0]).spliterator(), false)
                        .forEach(savedMatches::add);
                yield savedMatches;
            }
            case "findByExplanationIdInOrderByRankAsc" -> {
                Collection<UUID> explanationIds = searchIds(args[0]);
                yield savedMatches.stream()
                        .filter(match -> explanationIds.contains(match.getExplanationId()))
                        .toList();
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private Iterable<UserProductSearchResultItem> resultItems(Object value) {
        return (Iterable<UserProductSearchResultItem>) value;
    }

    @SuppressWarnings("unchecked")
    private Iterable<UserProductRecommendationExplanation> explanationEntities(Object value) {
        return (Iterable<UserProductRecommendationExplanation>) value;
    }

    @SuppressWarnings("unchecked")
    private Iterable<UserProductRecommendationFilterMatch> filterMatchEntities(Object value) {
        return (Iterable<UserProductRecommendationFilterMatch>) value;
    }

    @SuppressWarnings("unchecked")
    private Collection<UUID> searchIds(Object value) {
        return (Collection<UUID>) value;
    }

    @SuppressWarnings("unchecked")
    private Collection<String> productKeys(Object value) {
        return (Collection<String>) value;
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
        return search(userId, QUERY, NORMALIZED_QUERY, hasMoreProducts);
    }

    private UserProductSearch search(UUID userId, String query, String normalizedQuery, boolean hasMoreProducts) {
        return UserProductSearch.create(
                userId,
                query,
                normalizedQuery,
                PROFILE_HASH,
                SEARCH_VERSION,
                NOW,
                NOW.plusSeconds(3600),
                cachePolicyResolver()
                        .admitSearch(List.of(MerchantCatalogSourceIdentity.DISCOVERY_SOURCE))
                        .policyFingerprint(),
                hasMoreProducts
        );
    }

    private UserProductSearchCachePolicy cachePolicy() {
        return new UserProductSearchCachePolicy(cachePolicyResolver());
    }

    private CatalogDataUsePolicyResolver cachePolicyResolver() {
        return new CatalogDataUsePolicyResolver(
                List.of(new GenericUcpCatalogDataUsePolicy(
                        new GenericUcpCatalogDataUseProperties(Duration.ofHours(24), Duration.ofMinutes(2))
                )),
                new CatalogDataUsePolicyMetrics(new SimpleMeterRegistry())
        );
    }

    private List<UserProductSearchResultItem> items(UUID searchId, int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> item(searchId, index))
                .toList();
    }

    private List<UserProductSearchResultItem> items(UUID searchId, int start, int end) {
        return java.util.stream.IntStream.rangeClosed(start, end)
                .mapToObj(index -> item(searchId, index))
                .toList();
    }

    private UserProductSearchResultItem item(UUID searchId, int index) {
        return UserProductSearchResultItem.from(
                searchId,
                "merchant.example:item-" + index,
                "hash-" + index,
                product(index),
                NOW
        );
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
