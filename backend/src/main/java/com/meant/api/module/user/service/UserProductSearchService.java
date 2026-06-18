package com.meant.api.module.user.service;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.merchant.service.MerchantSemanticProductSearchService;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchCatalogInput;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserProductSearchService {

    private final UserSettingsService userSettingsService;
    private final MerchantSemanticProductSearchService merchantSemanticProductSearchService;
    private final UserProductSearchQueryUnderstandingService userProductSearchQueryUnderstandingService;
    private final UserProductSearchCatalogInputBuilder userProductSearchCatalogInputBuilder;
    private final UserProductSearchHashService userProductSearchHashService;
    private final UserProductRecommendationExplanationService userProductRecommendationExplanationService;
    private final UserProductSearchPersistenceService userProductSearchPersistenceService;
    private final UserProductSearchEventService userProductSearchEventService;
    private final UserInventoryService userInventoryService;
    private final UserProductSearchProperties userProductSearchProperties;
    private final OpenRouterProperties openRouterProperties;

    public UserProductSearchResult search(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid SearchUserProductsCommand command
    ) {
        if (!upsertCommand.id().equals(command.userId())) {
            throw UserException.forbidden("Product search user does not match authenticated user");
        }

        String query = command.query().trim();
        UserProductSearchQueryIntentResult queryIntent = userProductSearchQueryUnderstandingService.understand(query);
        UserSettingsResult settings = userSettingsService.get(upsertCommand);
        UserProductSearchCatalogInput catalogInput =
                userProductSearchCatalogInputBuilder.build(
                        query,
                        queryIntent,
                        settings,
                        command.buyerIp(),
                        command.userAgent()
                );
        String normalizedQuery = catalogInput.cacheKey();
        String profileHash = userProductSearchHashService.profileHash(settings)
                + ":" + userInventoryService.inventoryProfileHash(command.userId());
        Instant now = Instant.now();
        int offset = command.offset();
        int limit = command.limit();
        int fetchLimit = fetchLimit(offset, limit);

        UserProductSearchResult result;
        if (command.merchantId() != null) {
            result = searchWithoutPersisting(
                    command.userId(),
                    query,
                    catalogInput,
                    normalizedQuery,
                    profileHash,
                    settings,
                    command.merchantId(),
                    offset,
                    limit,
                    fetchLimit
            );
        } else {
            result = userProductSearchPersistenceService.findCachedSearch(
                            command.userId(),
                            query,
                            normalizedQuery,
                            profileHash,
                            userProductSearchProperties.searchVersion(),
                            openRouterProperties.models().productRecommendationExplainer(),
                            userProductSearchProperties.explanationPromptVersion(),
                            now,
                            offset,
                            limit
                    )
                    .orElseGet(() -> searchAndPersist(
                            command.userId(),
                            query,
                            catalogInput,
                            normalizedQuery,
                            profileHash,
                            settings,
                            now,
                            offset,
                            limit,
                            fetchLimit
                    ));
        }

        userProductSearchEventService.record(
                command.userId(),
                command.merchantId(),
                queryIntent,
                result.products().size(),
                now
        );
        return result;
    }

    private UserProductSearchResult searchWithoutPersisting(
            UUID userId,
            String query,
            UserProductSearchCatalogInput catalogInput,
            String normalizedQuery,
            String profileHash,
            UserSettingsResult settings,
            UUID merchantId,
            int offset,
            int limit,
            int fetchLimit
    ) {
        List<UserProductSearchProductSnapshot> products = productSnapshots(catalogInput, merchantId, fetchLimit);
        Map<String, UserInventoryRecommendationSignal> inventorySignals =
                userInventoryService.recommendationSignals(userId, products);
        Instant now = Instant.now();
        Map<String, UserProductRecommendationExplanationResult> explanations =
                userProductRecommendationExplanationService.explain(
                        userId,
                        query,
                        normalizedQuery,
                        profileHash,
                        settings,
                        products,
                        inventorySignals
                );
        List<UserProductSearchProductResult> productResults = products.stream()
                .filter(product -> explanations.containsKey(product.productKey()))
                .map(product -> UserProductSearchProductResult.from(
                        UserProductSearchResultItem.from(
                                UUID.randomUUID(),
                                product.productKey(),
                                product.productHash(),
                                product.product(),
                                now
                        ),
                        explanations.get(product.productKey())
                ))
                .sorted(java.util.Comparator.comparingInt(UserProductSearchProductResult::matchScore)
                        .reversed()
                        .thenComparingInt(UserProductSearchProductResult::rank))
                .toList();
        boolean hasMore = hasMoreProducts(products.size(), fetchLimit);
        return new UserProductSearchResult(
                query,
                normalizedQuery,
                profileHash,
                false,
                offset,
                limit,
                hasMore ? pageEnd(offset, limit) : null,
                hasMore,
                page(productResults, offset, limit)
        );
    }

    private UserProductSearchResult searchAndPersist(
            UUID userId,
            String query,
            UserProductSearchCatalogInput catalogInput,
            String normalizedQuery,
            String profileHash,
            UserSettingsResult settings,
            Instant now,
            int offset,
            int limit,
            int fetchLimit
    ) {
        List<UserProductSearchProductSnapshot> products = productSnapshots(catalogInput, null, fetchLimit);
        Map<String, UserInventoryRecommendationSignal> inventorySignals =
                userInventoryService.recommendationSignals(userId, products);
        Map<String, UserProductRecommendationExplanationResult> explanations =
                userProductRecommendationExplanationService.explain(
                        userId,
                        query,
                        normalizedQuery,
                        profileHash,
                        settings,
                        products,
                        inventorySignals
                );
        return userProductSearchPersistenceService.saveSearch(
                userId,
                query,
                normalizedQuery,
                profileHash,
                userProductSearchProperties.searchVersion(),
                now,
                now.plus(userProductSearchProperties.cacheTtl()),
                products,
                explanations,
                hasMoreProducts(products.size(), fetchLimit),
                offset,
                limit
        );
    }

    private List<UserProductSearchProductSnapshot> productSnapshots(
            UserProductSearchCatalogInput catalogInput,
            UUID merchantId,
            int productLimit
    ) {
        MerchantSemanticProductSearchResult searchResult = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(
                        catalogInput.searchQuery(),
                        merchantId,
                        null,
                        null,
                        null,
                        productLimit,
                        catalogInput.context(),
                        catalogInput.signals(),
                        catalogInput.filters()
                )
        );
        return safeProducts(searchResult).stream()
                .map(product -> new UserProductSearchProductSnapshot(
                        userProductSearchHashService.productKey(product),
                        userProductSearchHashService.productHash(product),
                        product
                ))
                .toList();
    }

    private List<MerchantSemanticProductResult> safeProducts(
            MerchantSemanticProductSearchResult searchResult
    ) {
        return searchResult == null || searchResult.products() == null ? List.of() : searchResult.products();
    }

    private int fetchLimit(int offset, int limit) {
        int pageEnd = pageEnd(offset, limit);
        if (pageEnd >= UserProductSearchPagination.MAX_RESULT_WINDOW) {
            return UserProductSearchPagination.MAX_RESULT_WINDOW;
        }
        return pageEnd + 1;
    }

    private boolean hasMoreProducts(int productCount, int fetchLimit) {
        return fetchLimit < UserProductSearchPagination.MAX_RESULT_WINDOW && productCount >= fetchLimit;
    }

    private List<UserProductSearchProductResult> page(
            List<UserProductSearchProductResult> products,
            int offset,
            int limit
    ) {
        int toIndex = Math.min(pageEnd(offset, limit), products.size());
        return offset >= products.size() ? List.of() : products.subList(offset, toIndex);
    }

    private int pageEnd(int offset, int limit) {
        return Math.min(offset + limit, UserProductSearchPagination.MAX_RESULT_WINDOW);
    }
}
