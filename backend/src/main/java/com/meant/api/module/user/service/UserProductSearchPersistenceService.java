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
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<UserProductSearchResult> findCachedSearch(
            UUID userId,
            String query,
            String normalizedQuery,
            String profileHash,
            String searchVersion,
            String model,
            String promptVersion,
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
        Map<String, UserProductSearchProductResult> products = new LinkedHashMap<>();
        for (UserProductSearch search : searches) {
            resultFromSearch(
                            search,
                            search.getQuery(),
                            search.getNormalizedQuery(),
                            profileHash,
                            model,
                            promptVersion,
                            true,
                            UserProductSearchPagination.DEFAULT_OFFSET,
                            UserProductSearchPagination.MAX_RESULT_WINDOW
                    )
                    .ifPresent(result -> result.products().forEach(product -> {
                        if (products.size() < productLimit) {
                            products.putIfAbsent(product.productKey(), product);
                        }
                    }));
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
        for (UserProductRecommendationExplanationResult explanation : explanations) {
            UserProductRecommendationExplanation entity = UserProductRecommendationExplanation.create(
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
            );
            userProductRecommendationExplanationRepository.save(entity);
            saveFilterMatches(entity.getId(), explanation.matchedFilterIds(), UserProductRecommendationFilterMatch.MATCHED, now);
            saveFilterMatches(entity.getId(), explanation.missedFilterIds(), UserProductRecommendationFilterMatch.MISSED, now);
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
            boolean hasMoreProducts,
            int offset,
            int limit
    ) {
        UserProductSearch search = userProductSearchRepository
                .findByUserIdAndNormalizedQueryAndProfileHashAndSearchVersion(
                        userId,
                        normalizedQuery,
                        profileHash,
                        searchVersion
                )
                .map(existing -> {
                    existing.refresh(query, now, expiresAt, hasMoreProducts);
                    return existing;
                })
                .orElseGet(() -> UserProductSearch.create(
                        userId,
                        query,
                        normalizedQuery,
                        profileHash,
                        searchVersion,
                        now,
                        expiresAt,
                        hasMoreProducts
                ));
        UserProductSearch savedSearch = userProductSearchRepository.save(search);
        userProductSearchResultItemRepository.deleteBySearchId(savedSearch.getId());
        List<UserProductSearchResultItem> items = products.stream()
                .filter(product -> explanations.containsKey(product.productKey()))
                .map(product -> UserProductSearchResultItem.from(
                        savedSearch.getId(),
                        product.productKey(),
                        product.productHash(),
                        product.product(),
                        now,
                        richCatalogSnapshot(product.product())
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
                productResults(items, explanations)
        );
    }

    private Optional<UserProductSearchResult> resultFromSearch(
            UserProductSearch search,
            String query,
            String normalizedQuery,
            String profileHash,
            String model,
            String promptVersion,
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
        return Optional.of(result(
                query,
                normalizedQuery,
                profileHash,
                cached,
                offset,
                limit,
                search.isHasMoreProducts(),
                products
        ));
    }

    private boolean canServePage(int itemCount, boolean hasMoreProducts, int offset, int limit) {
        return itemCount >= pageEnd(offset, limit) || !hasMoreProducts;
    }

    private UserProductSearchResult result(
            String query,
            String normalizedQuery,
            String profileHash,
            boolean cached,
            int offset,
            int limit,
            boolean hasMoreProducts,
            List<UserProductSearchProductResult> products
    ) {
        int pageEnd = pageEnd(offset, limit);
        int toIndex = Math.min(pageEnd, products.size());
        List<UserProductSearchProductResult> page = offset >= products.size()
                ? List.of()
                : products.subList(offset, toIndex);
        boolean hasMore = pageEnd < UserProductSearchPagination.MAX_RESULT_WINDOW
                && (products.size() > pageEnd || hasMoreProducts);
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

    private int pageEnd(int offset, int limit) {
        return Math.min(offset + limit, UserProductSearchPagination.MAX_RESULT_WINDOW);
    }

    private List<UserProductSearchProductResult> productResults(
            List<UserProductSearchResultItem> items,
            Map<String, UserProductRecommendationExplanationResult> explanations
    ) {
        return items.stream()
                .filter(item -> explanations.containsKey(item.getProductKey()))
                .sorted(Comparator.comparingInt(UserProductSearchResultItem::getRank))
                .map(item -> UserProductSearchProductResult.from(
                        item,
                        explanations.get(item.getProductKey()),
                        richCatalogData(item)
                ))
                .sorted(Comparator.comparingInt(UserProductSearchProductResult::matchScore)
                        .reversed()
                        .thenComparingInt(UserProductSearchProductResult::rank))
                .toList();
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
                        .filter(explanation -> expectedProductHashes
                                .getOrDefault(explanation.getProductKey(), "")
                                .equals(explanation.getProductHash()))
                        .toList();
        if (explanations.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UserProductRecommendationFilterMatch>> matches = userProductRecommendationFilterMatchRepository
                .findByExplanationIdInOrderByRankAsc(explanations.stream()
                        .map(UserProductRecommendationExplanation::getId)
                        .toList())
                .stream()
                .collect(Collectors.groupingBy(
                        UserProductRecommendationFilterMatch::getExplanationId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return explanations.stream()
                .collect(Collectors.toMap(
                        UserProductRecommendationExplanation::getProductKey,
                        explanation -> new UserProductRecommendationExplanationResult(
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
                        ),
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

    private void saveFilterMatches(
            UUID explanationId,
            List<String> filterIds,
            String matchType,
            Instant now
    ) {
        for (int index = 0; index < filterIds.size(); index++) {
            userProductRecommendationFilterMatchRepository.save(UserProductRecommendationFilterMatch.create(
                    explanationId,
                    filterIds.get(index),
                    matchType,
                    index + 1,
                    now
            ));
        }
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
}
