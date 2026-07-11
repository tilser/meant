package com.meant.api.module.user.service;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.entity.UserProductRecommendationExplanation;
import com.meant.api.module.user.entity.UserProductRecommendationFilterMatch;
import com.meant.api.module.user.entity.UserProductSearch;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.entity.UserProductSearchResultItem.RichCatalogSnapshot;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserProductRecommendationExplanationRepository;
import com.meant.api.module.user.repository.UserProductRecommendationFilterMatchRepository;
import com.meant.api.module.user.repository.UserProductSearchRepository;
import com.meant.api.module.user.repository.UserProductSearchResultItemRepository;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class UserProductSearchPersistenceService {

    private static final TypeReference<List<ProductCatalogMedia>> MEDIA_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProductCatalogCategory>> CATEGORY_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ProductCatalogAttribute>> ATTRIBUTE_LIST_TYPE = new TypeReference<>() {
    };

    private final UserProductSearchRepository userProductSearchRepository;
    private final UserProductSearchResultItemRepository userProductSearchResultItemRepository;
    private final UserProductRecommendationExplanationRepository userProductRecommendationExplanationRepository;
    private final UserProductRecommendationFilterMatchRepository userProductRecommendationFilterMatchRepository;
    private final UserTasteRankingService userTasteRankingService;
    private final UserProductSearchCurationPolicy userProductSearchCurationPolicy;
    private final ObjectMapper objectMapper;
    private final UserProductSearchCachePolicy cachePolicy;

    @Transactional(readOnly = true)
    public Optional<UserProductSearchResult> findCachedSearch(
            UUID userId,
            String query,
            String normalizedQuery,
            String profileHash,
            String searchVersion,
            String model,
            String promptVersion,
            UserTasteProfileResult tasteProfile,
            UserSettingsResult settings,
            Instant now,
            int offset,
            int limit
    ) {
        return userProductSearchRepository
                .findFirstByUserIdAndNormalizedQueryAndProfileHashAndSearchVersionAndExpiresAtAfterOrderByUpdatedAtDesc(
                        userId,
                        normalizedQuery,
                        profileHash,
                        searchVersion,
                        now
                )
                .flatMap(search -> {
                    List<UserProductSearchResultItem> items = userProductSearchResultItemRepository
                            .findBySearchIdOrderByRankAsc(search.getId());
                    if (!cachePolicy.isCurrent(search, items)) {
                        return Optional.empty();
                    }
                    if (!canServePage(items.size(), search.isHasMoreProducts(), offset, limit)) {
                        return Optional.empty();
                    }
                    return resultFromSearch(
                            search,
                            query,
                            normalizedQuery,
                            profileHash,
                            model,
                            promptVersion,
                            tasteProfile,
                            settings,
                            true,
                            offset,
                            limit,
                            items
                    );
                });
    }

    @Transactional(readOnly = true)
    public Map<String, UserProductRecommendationExplanationResult> findExplanations(
            UUID userId,
            String normalizedQuery,
            String profileHash,
            String model,
            String promptVersion,
            Collection<UserProductSearchProductSnapshot> products
    ) {
        return loadExplanations(
                userId,
                normalizedQuery,
                profileHash,
                model,
                promptVersion,
                products.stream()
                        .collect(Collectors.toMap(
                                UserProductSearchProductSnapshot::productKey,
                                UserProductSearchProductSnapshot::productHash,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ))
        );
    }

    @Transactional(readOnly = true)
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
        List<UserProductSearch> searches = userProductSearchRepository
                .findByUserIdAndProfileHashAndSearchVersionAndExpiresAtAfterOrderByUpdatedAtDesc(
                        userId,
                        profileHash,
                        searchVersion,
                        now,
                        PageRequest.of(0, recentSearchLimit)
                );
        if (searches.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserProductSearch> searchesById = new LinkedHashMap<>();
        for (UserProductSearch search : searches) {
            searchesById.put(search.getId(), search);
        }
        Map<UUID, List<UserProductSearchResultItem>> itemsBySearchId = userProductSearchResultItemRepository
                .findBySearchIdIn(searchesById.keySet())
                .stream()
                .collect(Collectors.groupingBy(UserProductSearchResultItem::getSearchId));
        Map<RecentExplanationKey, UserProductRecommendationExplanationResult> explanations =
                loadRecentExplanations(userId, profileHash, model, promptVersion, searchesById, itemsBySearchId);
        Map<String, UserProductSearchProductResult> products = new LinkedHashMap<>();
        for (UserProductSearch search : searches) {
            List<UserProductSearchResultItem> items = itemsBySearchId.getOrDefault(search.getId(), List.of());
            if (!cachePolicy.isCurrent(search, items)) {
                continue;
            }
            List<UserProductSearchProductResult> recentProducts = visibleRankedProducts(
                    productResults(items, explanationsForSearch(search, items, explanations)),
                    null,
                    null
            );
            for (UserProductSearchProductResult product : recentProducts) {
                if (products.size() < productLimit) {
                    products.putIfAbsent(product.productKey(), product);
                }
                if (products.size() >= productLimit) {
                    break;
                }
            }
            if (products.size() >= productLimit) {
                break;
            }
        }
        return products.values().stream()
                .limit(productLimit)
                .toList();
    }

    @Transactional
    public Map<String, UserProductRecommendationExplanationResult> saveExplanations(
            UUID userId,
            String normalizedQuery,
            String profileHash,
            String model,
            String promptVersion,
            List<UserProductRecommendationExplanationResult> explanations,
            Instant now
    ) {
        List<UserProductRecommendationExplanation> entities = explanations.stream()
                .map(explanation -> UserProductRecommendationExplanation.create(
                        userId,
                        normalizedQuery,
                        profileHash,
                        explanation.productKey(),
                        explanation.productHash(),
                        model,
                        promptVersion,
                        explanation.whyMeantForYou(),
                        explanation.inventoryRelationship() == null ? null : explanation.inventoryRelationship().name(),
                        explanation.inventoryItemId(),
                        explanation.inventoryItemName(),
                        now
                ))
                .toList();
        List<UserProductRecommendationExplanation> savedEntities =
                userProductRecommendationExplanationRepository.saveAll(entities);
        Map<ProductExplanationKey, UserProductRecommendationExplanationResult> explanationsByProduct = explanations.stream()
                .collect(Collectors.toMap(
                        explanation -> new ProductExplanationKey(explanation.productKey(), explanation.productHash()),
                        explanation -> explanation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<UserProductRecommendationFilterMatch> filterMatches = new ArrayList<>();
        for (UserProductRecommendationExplanation entity : savedEntities) {
            UserProductRecommendationExplanationResult explanation = explanationsByProduct.get(
                    new ProductExplanationKey(entity.getProductKey(), entity.getProductHash())
            );
            if (explanation == null) {
                continue;
            }
            filterMatches.addAll(filterMatches(
                    entity.getId(),
                    explanation.matchedFilterIds(),
                    UserProductRecommendationFilterMatch.MATCHED,
                    now
            ));
            filterMatches.addAll(filterMatches(
                    entity.getId(),
                    explanation.missedFilterIds(),
                    UserProductRecommendationFilterMatch.MISSED,
                    now
            ));
        }
        if (!filterMatches.isEmpty()) {
            userProductRecommendationFilterMatchRepository.saveAll(filterMatches);
        }

        return loadExplanations(
                userId,
                normalizedQuery,
                profileHash,
                model,
                promptVersion,
                explanations.stream()
                        .collect(Collectors.toMap(
                                UserProductRecommendationExplanationResult::productKey,
                                UserProductRecommendationExplanationResult::productHash,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ))
        );
    }

    @Transactional
    public UserProductSearchResult saveSearch(
            UUID userId,
            String query,
            String normalizedQuery,
            String profileHash,
            String searchVersion,
            Instant now,
            Instant expiresAt,
            List<UserProductSearchProductSnapshot> products,
            Map<String, UserProductRecommendationExplanationResult> explanations,
            UserTasteProfileResult tasteProfile,
            UserSettingsResult settings,
            boolean hasMoreProducts,
            int offset,
            int limit
    ) {
        return saveSearch(
                userId,
                query,
                normalizedQuery,
                profileHash,
                searchVersion,
                now,
                expiresAt,
                products,
                products.stream().map(UserProductSearchProductSnapshot::discoverySource).distinct().toList(),
                explanations,
                tasteProfile,
                settings,
                hasMoreProducts,
                offset,
                limit
        );
    }

    @Transactional
    public UserProductSearchResult saveSearch(
            UUID userId,
            String query,
            String normalizedQuery,
            String profileHash,
            String searchVersion,
            Instant now,
            Instant expiresAt,
            List<UserProductSearchProductSnapshot> products,
            Collection<DiscoverySourceIdentity> discoverySources,
            Map<String, UserProductRecommendationExplanationResult> explanations,
            UserTasteProfileResult tasteProfile,
            UserSettingsResult settings,
            boolean hasMoreProducts,
            int offset,
            int limit
    ) {
        UserProductSearchCachePolicy.WriteDecision retention = cachePolicy.decide(
                products,
                discoverySources,
                now,
                expiresAt
        );
        if (!retention.persist()) {
            return resultWithoutPersistence(
                    query,
                    normalizedQuery,
                    profileHash,
                    products,
                    explanations,
                    tasteProfile,
                    settings,
                    hasMoreProducts,
                    offset,
                    limit,
                    now
            );
        }
        UserProductSearch search = userProductSearchRepository
                .findByUserIdAndNormalizedQueryAndProfileHashAndSearchVersion(
                        userId,
                        normalizedQuery,
                        profileHash,
                        searchVersion
                )
                .map(existing -> {
                    existing.refresh(
                            query,
                            now,
                            retention.expiresAt(),
                            retention.policyFingerprint(),
                            hasMoreProducts
                    );
                    return existing;
                })
                .orElseGet(() -> UserProductSearch.create(
                        userId,
                        query,
                        normalizedQuery,
                        profileHash,
                        searchVersion,
                        now,
                        retention.expiresAt(),
                        retention.policyFingerprint(),
                        hasMoreProducts
                ));
        UserProductSearch savedSearch = userProductSearchRepository.save(search);
        userProductSearchResultItemRepository.deleteBySearchId(savedSearch.getId());
        List<UserProductSearchResultItem> items = uniqueProducts(products).stream()
                .map(product -> UserProductSearchResultItem.from(
                        savedSearch.getId(),
                        product.productKey(),
                        product.productHash(),
                        product.product(),
                        now,
                        richCatalogSnapshot(product.product()),
                        product.discoverySource()
                ))
                .toList();
        userProductSearchResultItemRepository.saveAll(items);
        return result(
                query,
                normalizedQuery,
                profileHash,
                false,
                offset,
                limit,
                hasMoreProducts,
                visibleRankedProducts(productResults(items, explanations), tasteProfile, settings)
        );
    }

    private UserProductSearchResult resultWithoutPersistence(
            String query,
            String normalizedQuery,
            String profileHash,
            List<UserProductSearchProductSnapshot> products,
            Map<String, UserProductRecommendationExplanationResult> explanations,
            UserTasteProfileResult tasteProfile,
            UserSettingsResult settings,
            boolean hasMoreProducts,
            int offset,
            int limit,
            Instant now
    ) {
        UUID transientSearchId = UUID.randomUUID();
        List<UserProductSearchResultItem> items = uniqueProducts(products).stream()
                .map(product -> UserProductSearchResultItem.from(
                        transientSearchId,
                        product.productKey(),
                        product.productHash(),
                        product.product(),
                        now,
                        richCatalogSnapshot(product.product()),
                        product.discoverySource()
                ))
                .toList();
        return result(
                query,
                normalizedQuery,
                profileHash,
                false,
                offset,
                limit,
                hasMoreProducts,
                visibleRankedProducts(productResults(items, explanations), tasteProfile, settings)
        );
    }

    private List<UserProductSearchProductSnapshot> uniqueProducts(
            List<UserProductSearchProductSnapshot> products
    ) {
        Map<String, UserProductSearchProductSnapshot> uniqueProducts = new LinkedHashMap<>();
        products.forEach(product -> uniqueProducts.putIfAbsent(product.productKey(), product));
        return uniqueProducts.values().stream().toList();
    }

    private Optional<UserProductSearchResult> resultFromSearch(
            UserProductSearch search,
            String query,
            String normalizedQuery,
            String profileHash,
            String model,
            String promptVersion,
            UserTasteProfileResult tasteProfile,
            UserSettingsResult settings,
            boolean cached,
            int offset,
            int limit
    ) {
        List<UserProductSearchResultItem> items = userProductSearchResultItemRepository
                .findBySearchIdOrderByRankAsc(search.getId());
        return resultFromSearch(
                search,
                query,
                normalizedQuery,
                profileHash,
                model,
                promptVersion,
                tasteProfile,
                settings,
                cached,
                offset,
                limit,
                items
        );
    }

    private Optional<UserProductSearchResult> resultFromSearch(
            UserProductSearch search,
            String query,
            String normalizedQuery,
            String profileHash,
            String model,
            String promptVersion,
            UserTasteProfileResult tasteProfile,
            UserSettingsResult settings,
            boolean cached,
            int offset,
            int limit,
            List<UserProductSearchResultItem> items
    ) {
        Map<String, UserProductRecommendationExplanationResult> explanations = loadExplanations(
                search.getUserId(),
                normalizedQuery,
                profileHash,
                model,
                promptVersion,
                items.stream()
                        .collect(Collectors.toMap(
                                UserProductSearchResultItem::getProductKey,
                                UserProductSearchResultItem::getProductHash,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ))
        );
        List<UserProductSearchProductResult> products = productResults(items, explanations);
        if (!items.isEmpty() && products.isEmpty()) {
            return Optional.empty();
        }
        List<UserProductSearchProductResult> visibleProducts = visibleRankedProducts(products, tasteProfile, settings);
        if (!canServeVisiblePage(visibleProducts.size(), search.isHasMoreProducts(), offset, limit)) {
            return Optional.empty();
        }
        return Optional.of(result(
                query,
                normalizedQuery,
                profileHash,
                cached,
                offset,
                limit,
                search.isHasMoreProducts(),
                visibleProducts
        ));
    }

    private boolean canServePage(int itemCount, boolean hasMoreProducts, int offset, int limit) {
        return itemCount >= pageEnd(offset, limit) || !hasMoreProducts;
    }

    private boolean canServeVisiblePage(int visibleCount, boolean hasMoreProducts, int offset, int limit) {
        return offset == 0 || canServePage(visibleCount, hasMoreProducts, offset, limit);
    }

    private UserProductSearchResult result(
            String query,
            String normalizedQuery,
            String profileHash,
            boolean cached,
            int offset,
            int limit,
            boolean hasMoreProducts,
            List<UserProductSearchProductResult> rankedProducts
    ) {
        int pageEnd = pageEnd(offset, limit);
        int toIndex = Math.min(pageEnd, rankedProducts.size());
        List<UserProductSearchProductResult> page = offset >= rankedProducts.size()
                ? List.of()
                : rankedProducts.subList(offset, toIndex);
        boolean hasMore = pageEnd < UserProductSearchPagination.MAX_RESULT_WINDOW
                && (rankedProducts.size() > pageEnd || hasMoreProducts);
        Integer nextOffset = hasMore ? pageEnd : null;
        return new UserProductSearchResult(
                query,
                normalizedQuery,
                profileHash,
                cached,
                offset,
                limit,
                nextOffset,
                hasMore,
                page
        );
    }

    private List<UserProductSearchProductResult> visibleRankedProducts(
            List<UserProductSearchProductResult> products,
            UserTasteProfileResult tasteProfile,
            UserSettingsResult settings
    ) {
        return userProductSearchCurationPolicy.visibleProducts(
                userTasteRankingService.rank(products, tasteProfile, settings),
                settings
        );
    }

    private int pageEnd(int offset, int limit) {
        return Math.min(offset + limit, UserProductSearchPagination.MAX_RESULT_WINDOW);
    }

    private List<UserProductSearchProductResult> productResults(
            List<UserProductSearchResultItem> items,
            Map<String, UserProductRecommendationExplanationResult> explanations
    ) {
        Map<String, UserProductRecommendationExplanationResult> safeExplanations =
                explanations == null ? Map.of() : explanations;
        return items.stream()
                .sorted(Comparator.comparingInt(UserProductSearchResultItem::getRank))
                .map(item -> UserProductSearchProductResult.from(
                        item,
                        explanationFor(item, safeExplanations),
                        richCatalogData(item)
                ))
                .sorted(Comparator.comparingInt(UserProductSearchProductResult::matchScore)
                        .reversed()
                        .thenComparingInt(UserProductSearchProductResult::rank))
                .toList();
    }

    private Map<String, UserProductRecommendationExplanationResult> explanationsForSearch(
            UserProductSearch search,
            List<UserProductSearchResultItem> items,
            Map<RecentExplanationKey, UserProductRecommendationExplanationResult> explanations
    ) {
        return items.stream()
                .map(UserProductSearchResultItem::getProductKey)
                .distinct()
                .map(productKey -> explanations.get(new RecentExplanationKey(search.getNormalizedQuery(), productKey)))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        UserProductRecommendationExplanationResult::productKey,
                        explanation -> explanation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private UserProductRecommendationExplanationResult explanationFor(
            UserProductSearchResultItem item,
            Map<String, UserProductRecommendationExplanationResult> explanations
    ) {
        UserProductRecommendationExplanationResult explanation = explanations.get(item.getProductKey());
        return explanation == null
                ? UserProductRecommendationExplanationResult.fallback(item.getProductKey(), item.getProductHash())
                : explanation;
    }

    private RichCatalogSnapshot richCatalogSnapshot(MerchantSemanticProductResult product) {
        return new RichCatalogSnapshot(
                toJson(product.media()),
                toJson(product.categories()),
                toJson(product.certifications()),
                toJson(product.materials()),
                toJson(product.skus()),
                toJson(product.collections()),
                toJson(product.attributes())
        );
    }

    private UserProductSearchProductResult.RichCatalogData richCatalogData(UserProductSearchResultItem item) {
        return new UserProductSearchProductResult.RichCatalogData(
                fromJson(item.getMediaJson(), MEDIA_LIST_TYPE, List.<ProductCatalogMedia>of()),
                fromJson(item.getCategoriesJson(), CATEGORY_LIST_TYPE, List.<ProductCatalogCategory>of()),
                fromJson(item.getCertificationsJson(), STRING_LIST_TYPE, List.<String>of()),
                fromJson(item.getMaterialsJson(), STRING_LIST_TYPE, List.<String>of()),
                fromJson(item.getSkusJson(), STRING_LIST_TYPE, List.<String>of()),
                fromJson(item.getCollectionsJson(), STRING_LIST_TYPE, List.<String>of()),
                fromJson(item.getAttributesJson(), ATTRIBUTE_LIST_TYPE, List.<ProductCatalogAttribute>of())
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JacksonException exception) {
            throw new UserException("Could not serialize product search catalog data", exception);
        }
    }

    private <T> T fromJson(String value, TypeReference<T> type, T defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            T parsed = objectMapper.readValue(value, type);
            return parsed == null ? defaultValue : parsed;
        } catch (JacksonException exception) {
            throw new UserException("Could not parse product search catalog data", exception);
        }
    }

    private Map<String, UserProductRecommendationExplanationResult> loadExplanations(
            UUID userId,
            String normalizedQuery,
            String profileHash,
            String model,
            String promptVersion,
            Map<String, String> expectedProductHashes
    ) {
        if (expectedProductHashes.isEmpty()) {
            return Map.of();
        }
        List<UserProductRecommendationExplanation> explanations =
                userProductRecommendationExplanationRepository
                        .findByUserIdAndNormalizedQueryAndProfileHashAndModelAndPromptVersionAndProductKeyIn(
                                userId,
                                normalizedQuery,
                                profileHash,
                                model,
                                promptVersion,
                                expectedProductHashes.keySet()
                        ).stream()
                        .filter(explanation -> matchesExpectedProductHash(
                                expectedProductHashes.get(explanation.getProductKey()),
                                explanation.getProductHash()
                        ))
                        .toList();
        if (explanations.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UserProductRecommendationFilterMatch>> matches = filterMatches(explanations);

        return explanations.stream()
                .collect(Collectors.toMap(
                        UserProductRecommendationExplanation::getProductKey,
                        explanation -> explanationResult(explanation, matches),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<RecentExplanationKey, UserProductRecommendationExplanationResult> loadRecentExplanations(
            UUID userId,
            String profileHash,
            String model,
            String promptVersion,
            Map<UUID, UserProductSearch> searchesById,
            Map<UUID, List<UserProductSearchResultItem>> itemsBySearchId
    ) {
        Map<RecentExplanationKey, String> expectedProductHashes = new LinkedHashMap<>();
        for (UserProductSearch search : searchesById.values()) {
            List<UserProductSearchResultItem> items = itemsBySearchId.getOrDefault(search.getId(), List.of());
            items.forEach(item -> expectedProductHashes.putIfAbsent(
                    new RecentExplanationKey(search.getNormalizedQuery(), item.getProductKey()),
                    item.getProductHash()
            ));
        }
        if (expectedProductHashes.isEmpty()) {
            return Map.of();
        }
        Set<String> productKeys = expectedProductHashes.keySet().stream()
                .map(RecentExplanationKey::productKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<UserProductRecommendationExplanation> explanations =
                userProductRecommendationExplanationRepository
                        .findByUserIdAndProfileHashAndModelAndPromptVersionAndProductKeyIn(
                                userId,
                                profileHash,
                                model,
                                promptVersion,
                                productKeys
                        ).stream()
                        .filter(explanation -> matchesExpectedProductHash(
                                expectedProductHashes.get(new RecentExplanationKey(
                                        explanation.getNormalizedQuery(),
                                        explanation.getProductKey()
                                )),
                                explanation.getProductHash()
                        ))
                        .toList();
        if (explanations.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UserProductRecommendationFilterMatch>> matches = filterMatches(explanations);
        return explanations.stream()
                .collect(Collectors.toMap(
                        explanation -> new RecentExplanationKey(
                                explanation.getNormalizedQuery(),
                                explanation.getProductKey()
                        ),
                        explanation -> explanationResult(explanation, matches),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private UserInventoryRecommendationRelationship inventoryRelationship(String value) {
        if (value == null || value.isBlank()) {
            return UserInventoryRecommendationRelationship.NONE;
        }
        try {
            return UserInventoryRecommendationRelationship.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return UserInventoryRecommendationRelationship.NONE;
        }
    }

    private boolean matchesExpectedProductHash(String expectedProductHash, String actualProductHash) {
        return expectedProductHash != null && expectedProductHash.equals(actualProductHash);
    }

    private Map<UUID, List<UserProductRecommendationFilterMatch>> filterMatches(
            List<UserProductRecommendationExplanation> explanations
    ) {
        return userProductRecommendationFilterMatchRepository
                .findByExplanationIdInOrderByRankAsc(explanations.stream()
                        .map(UserProductRecommendationExplanation::getId)
                        .toList())
                .stream()
                .collect(Collectors.groupingBy(
                        UserProductRecommendationFilterMatch::getExplanationId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private UserProductRecommendationExplanationResult explanationResult(
            UserProductRecommendationExplanation explanation,
            Map<UUID, List<UserProductRecommendationFilterMatch>> matches
    ) {
        return new UserProductRecommendationExplanationResult(
                explanation.getProductKey(),
                explanation.getProductHash(),
                explanation.getWhyMeantForYou(),
                filterIds(matches.getOrDefault(explanation.getId(), List.of()),
                        UserProductRecommendationFilterMatch.MATCHED),
                filterIds(matches.getOrDefault(explanation.getId(), List.of()),
                        UserProductRecommendationFilterMatch.MISSED),
                inventoryRelationship(explanation.getInventoryRelationship()),
                explanation.getInventoryItemId(),
                explanation.getInventoryItemName()
        );
    }

    private List<UserProductRecommendationFilterMatch> filterMatches(
            UUID explanationId,
            List<String> filterIds,
            String matchType,
            Instant now
    ) {
        if (filterIds == null || filterIds.isEmpty()) {
            return List.of();
        }
        List<UserProductRecommendationFilterMatch> matches = new ArrayList<>();
        int index = 1;
        for (String filterId : filterIds) {
            matches.add(UserProductRecommendationFilterMatch.create(
                    explanationId,
                    filterId,
                    matchType,
                    index++,
                    now
            ));
        }
        return matches;
    }

    private List<String> filterIds(
            List<UserProductRecommendationFilterMatch> matches,
            String matchType
    ) {
        return matches.stream()
                .filter(match -> matchType.equals(match.getMatchType()))
                .sorted(Comparator.comparingInt(UserProductRecommendationFilterMatch::getRank))
                .map(UserProductRecommendationFilterMatch::getFilterId)
                .toList();
    }

    private record RecentExplanationKey(String normalizedQuery, String productKey) {
    }

    private record ProductExplanationKey(String productKey, String productHash) {
    }
}
