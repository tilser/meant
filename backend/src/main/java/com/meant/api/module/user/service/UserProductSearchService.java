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
import com.meant.api.module.user.service.dto.UserProductSearchProductResult.RichCatalogData;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import com.meant.api.module.user.service.dto.UserProductSearchStreamEvent;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
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
    private final UserTasteProfileService userTasteProfileService;
    private final UserTasteRankingService userTasteRankingService;
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
        UserTasteProfileResult tasteProfile = userTasteProfileService.profile(command.userId(), settings);
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
                + ":" + userInventoryService.inventoryProfileHash(command.userId())
                + ":" + tasteProfile.profileHash();
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
                    tasteProfile,
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
                            tasteProfile,
                            settings,
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
                            tasteProfile,
                            now,
                            offset,
                            limit,
                            fetchLimit
                    ));
        }

        recordSearch(command, queryIntent, result, now);
        return result;
    }

    public void stream(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid SearchUserProductsCommand command,
            Consumer<UserProductSearchStreamEvent> eventConsumer
    ) {
        if (!upsertCommand.id().equals(command.userId())) {
            throw UserException.forbidden("Product search user does not match authenticated user");
        }

        eventConsumer.accept(UserProductSearchStreamEvent.phase("query", "Understanding your request"));
        String query = command.query().trim();
        UserProductSearchQueryIntentResult queryIntent = userProductSearchQueryUnderstandingService.understand(query);

        eventConsumer.accept(UserProductSearchStreamEvent.phase("profile", "Loading your shopping context"));
        UserSettingsResult settings = userSettingsService.get(upsertCommand);
        UserTasteProfileResult tasteProfile = userTasteProfileService.profile(command.userId(), settings);
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
                + ":" + userInventoryService.inventoryProfileHash(command.userId())
                + ":" + tasteProfile.profileHash();
        Instant now = Instant.now();
        int offset = command.offset();
        int limit = command.limit();
        int fetchLimit = fetchLimit(offset, limit);

        if (command.merchantId() == null) {
            UserProductSearchResult cached = userProductSearchPersistenceService.findCachedSearch(
                            command.userId(),
                            query,
                            normalizedQuery,
                            profileHash,
                            userProductSearchProperties.searchVersion(),
                            openRouterProperties.models().productRecommendationExplainer(),
                            userProductSearchProperties.explanationPromptVersion(),
                            tasteProfile,
                            settings,
                            now,
                            offset,
                            limit
                    )
                    .orElse(null);
            if (cached != null) {
                eventConsumer.accept(UserProductSearchStreamEvent.phase("cache", "Restoring cached matches"));
                cached.products().forEach(product -> eventConsumer.accept(
                        UserProductSearchStreamEvent.productUpdate("cache", "Cached match ready", product)
                ));
                eventConsumer.accept(UserProductSearchStreamEvent.rankUpdate(
                        "ranking",
                        "Ordering cached matches",
                        cached.products()
                ));
                eventConsumer.accept(UserProductSearchStreamEvent.done(cached));
                recordSearch(command, queryIntent, cached, now);
                return;
            }
        }

        eventConsumer.accept(UserProductSearchStreamEvent.phase("catalog", "Searching merchant catalogs"));
        Map<String, UserProductSearchProductSnapshot> emittedCandidates = new LinkedHashMap<>();
        List<UserProductSearchProductSnapshot> fetchedProducts = productSnapshots(
                catalogInput,
                command.merchantId(),
                fetchLimit,
                candidate -> {
                    int rank = candidate.product().rank();
                    if (rank <= offset || rank > pageEnd(offset, limit)) {
                        return;
                    }
                    if (emittedCandidates.putIfAbsent(candidate.productKey(), candidate) == null) {
                        eventConsumer.accept(UserProductSearchStreamEvent.product(
                                "catalog",
                                "Product candidate found",
                                previewProductResult(candidate, now)
                        ));
                    }
                }
        );
        List<UserProductSearchProductSnapshot> products = uniqueProducts(fetchedProducts);

        eventConsumer.accept(UserProductSearchStreamEvent.phase("taste", "Checking inventory and taste signals"));
        Map<String, UserInventoryRecommendationSignal> inventorySignals =
                userInventoryService.recommendationSignals(command.userId(), products);

        eventConsumer.accept(UserProductSearchStreamEvent.phase("reasoning", "Writing fit explanations"));
        Map<String, UserProductRecommendationExplanationResult> explanations =
                userProductRecommendationExplanationService.explain(
                        command.userId(),
                        query,
                        normalizedQuery,
                        profileHash,
                        settings,
                        products,
                        inventorySignals
                );
        List<UserProductSearchProductResult> productResults = explainedProductResults(products, explanations, now);
        productResults = userTasteRankingService.rank(productResults, tasteProfile, settings);
        List<UserProductSearchProductResult> pageResults = page(productResults, offset, limit);
        pageResults.forEach(product -> eventConsumer.accept(UserProductSearchStreamEvent.productUpdate(
                "reasoning",
                "Product fit updated",
                product
        )));

        eventConsumer.accept(UserProductSearchStreamEvent.phase("ranking", "Ordering by relevance"));
        eventConsumer.accept(UserProductSearchStreamEvent.rankUpdate("ranking", "Relevance order updated", pageResults));

        boolean hasMoreProducts = hasMoreProducts(fetchedProducts.size(), fetchLimit);
        UserProductSearchResult result = command.merchantId() == null
                ? userProductSearchPersistenceService.saveSearch(
                        command.userId(),
                        query,
                        normalizedQuery,
                        profileHash,
                        userProductSearchProperties.searchVersion(),
                        now,
                        now.plus(userProductSearchProperties.cacheTtl()),
                        products,
                        explanations,
                        tasteProfile,
                        settings,
                        hasMoreProducts,
                        offset,
                        limit
                )
                : new UserProductSearchResult(
                        query,
                        normalizedQuery,
                        profileHash,
                        false,
                        offset,
                        limit,
                        hasMoreProducts ? pageEnd(offset, limit) : null,
                        hasMoreProducts,
                        pageResults
                );
        eventConsumer.accept(UserProductSearchStreamEvent.done(result));
        recordSearch(command, queryIntent, result, now);
    }

    private void recordSearch(
            SearchUserProductsCommand command,
            UserProductSearchQueryIntentResult queryIntent,
            UserProductSearchResult result,
            Instant now
    ) {
        userProductSearchEventService.record(
                command.userId(),
                command.merchantId(),
                queryIntent,
                result.products().size(),
                now
        );
        userTasteProfileService.recordSearch(command.userId(), queryIntent, now);
    }

    private UserProductSearchResult searchWithoutPersisting(
            UUID userId,
            String query,
            UserProductSearchCatalogInput catalogInput,
            String normalizedQuery,
            String profileHash,
            UserSettingsResult settings,
            UserTasteProfileResult tasteProfile,
            UUID merchantId,
            int offset,
            int limit,
            int fetchLimit
    ) {
        List<UserProductSearchProductSnapshot> fetchedProducts = productSnapshots(catalogInput, merchantId, fetchLimit);
        List<UserProductSearchProductSnapshot> products = uniqueProducts(fetchedProducts);
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
        List<UserProductSearchProductResult> productResults = explainedProductResults(products, explanations, now);
        productResults = userTasteRankingService.rank(productResults, tasteProfile, settings);
        boolean hasMore = hasMoreProducts(fetchedProducts.size(), fetchLimit);
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
            UserTasteProfileResult tasteProfile,
            Instant now,
            int offset,
            int limit,
            int fetchLimit
    ) {
        List<UserProductSearchProductSnapshot> fetchedProducts = productSnapshots(catalogInput, null, fetchLimit);
        List<UserProductSearchProductSnapshot> products = uniqueProducts(fetchedProducts);
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
                tasteProfile,
                settings,
                hasMoreProducts(fetchedProducts.size(), fetchLimit),
                offset,
                limit
        );
    }

    private List<UserProductSearchProductSnapshot> productSnapshots(
            UserProductSearchCatalogInput catalogInput,
            UUID merchantId,
            int productLimit
    ) {
        return productSnapshots(catalogInput, merchantId, productLimit, product -> {
        });
    }

    private List<UserProductSearchProductSnapshot> productSnapshots(
            UserProductSearchCatalogInput catalogInput,
            UUID merchantId,
            int productLimit,
            Consumer<UserProductSearchProductSnapshot> candidateConsumer
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
                ),
                candidate -> candidateConsumer.accept(productSnapshot(candidate))
        );
        return safeProducts(searchResult).stream()
                .map(this::productSnapshot)
                .toList();
    }

    private UserProductSearchProductSnapshot productSnapshot(MerchantSemanticProductResult product) {
        return new UserProductSearchProductSnapshot(
                userProductSearchHashService.productKey(product),
                userProductSearchHashService.productHash(product),
                product
        );
    }

    private List<UserProductSearchProductResult> explainedProductResults(
            List<UserProductSearchProductSnapshot> products,
            Map<String, UserProductRecommendationExplanationResult> explanations,
            Instant now
    ) {
        return products.stream()
                .filter(product -> explanations.containsKey(product.productKey()))
                .map(product -> productResult(product, explanations.get(product.productKey()), now))
                .toList();
    }

    private UserProductSearchProductResult previewProductResult(
            UserProductSearchProductSnapshot product,
            Instant now
    ) {
        return productResult(
                product,
                new UserProductRecommendationExplanationResult(
                        product.productKey(),
                        product.productHash(),
                        "Assessing fit for your request.",
                        List.of(),
                        List.of()
                ),
                now
        );
    }

    private UserProductSearchProductResult productResult(
            UserProductSearchProductSnapshot product,
            UserProductRecommendationExplanationResult explanation,
            Instant now
    ) {
        return UserProductSearchProductResult.from(
                UserProductSearchResultItem.from(
                        UUID.randomUUID(),
                        product.productKey(),
                        product.productHash(),
                        product.product(),
                        now
                ),
                explanation,
                richCatalogData(product.product())
        );
    }

    private RichCatalogData richCatalogData(MerchantSemanticProductResult product) {
        return new RichCatalogData(
                safeList(product.media()),
                safeList(product.categories()),
                safeList(product.certifications()),
                safeList(product.materials()),
                safeList(product.skus()),
                safeList(product.collections()),
                safeList(product.attributes())
        );
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private List<UserProductSearchProductSnapshot> uniqueProducts(
            List<UserProductSearchProductSnapshot> products
    ) {
        return products.stream()
                .collect(LinkedHashMap<String, UserProductSearchProductSnapshot>::new,
                        (uniqueProducts, product) -> uniqueProducts.putIfAbsent(product.productKey(), product),
                        LinkedHashMap::putAll)
                .values()
                .stream()
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
