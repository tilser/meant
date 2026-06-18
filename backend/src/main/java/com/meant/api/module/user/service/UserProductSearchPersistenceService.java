package com.meant.api.module.user.service;

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
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProductSearchPersistenceService {

    private final UserProductSearchRepository userProductSearchRepository;
    private final UserProductSearchResultItemRepository userProductSearchResultItemRepository;
    private final UserProductRecommendationExplanationRepository userProductRecommendationExplanationRepository;
    private final UserProductRecommendationFilterMatchRepository userProductRecommendationFilterMatchRepository;

    @Transactional(readOnly = true)
    public Optional<UserProductSearchResult> findCachedSearch(
            UUID userId,
            String query,
            String normalizedQuery,
            String profileHash,
            String searchVersion,
            String model,
            String promptVersion,
            Instant now
    ) {
        return userProductSearchRepository
                .findFirstByUserIdAndNormalizedQueryAndProfileHashAndSearchVersionAndExpiresAtAfterOrderByUpdatedAtDesc(
                        userId,
                        normalizedQuery,
                        profileHash,
                        searchVersion,
                        now
                )
                .flatMap(search -> resultFromSearch(
                        search,
                        query,
                        normalizedQuery,
                        profileHash,
                        model,
                        promptVersion,
                        true
                ));
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
                            true
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
            Map<String, UserProductRecommendationExplanationResult> explanations
    ) {
        UserProductSearch search = userProductSearchRepository
                .findByUserIdAndNormalizedQueryAndProfileHashAndSearchVersion(
                        userId,
                        normalizedQuery,
                        profileHash,
                        searchVersion
                )
                .map(existing -> {
                    existing.refresh(query, now, expiresAt);
                    return existing;
                })
                .orElseGet(() -> UserProductSearch.create(
                        userId,
                        query,
                        normalizedQuery,
                        profileHash,
                        searchVersion,
                        now,
                        expiresAt
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
                        now
                ))
                .toList();
        userProductSearchResultItemRepository.saveAll(items);
        return new UserProductSearchResult(
                query,
                normalizedQuery,
                profileHash,
                false,
                productResults(items, explanations)
        );
    }

    private java.util.Optional<UserProductSearchResult> resultFromSearch(
            UserProductSearch search,
            String query,
            String normalizedQuery,
            String profileHash,
            String model,
            String promptVersion,
            boolean cached
    ) {
        List<UserProductSearchResultItem> items = userProductSearchResultItemRepository
                .findBySearchIdOrderByRankAsc(search.getId());
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
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new UserProductSearchResult(
                query,
                normalizedQuery,
                profileHash,
                cached,
                products
        ));
    }

    private List<UserProductSearchProductResult> productResults(
            List<UserProductSearchResultItem> items,
            Map<String, UserProductRecommendationExplanationResult> explanations
    ) {
        return items.stream()
                .filter(item -> explanations.containsKey(item.getProductKey()))
                .sorted(Comparator.comparingInt(UserProductSearchResultItem::getRank))
                .map(item -> UserProductSearchProductResult.from(item, explanations.get(item.getProductKey())))
                .toList();
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
                                        UserProductRecommendationFilterMatch.MISSED)
                        ),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
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
