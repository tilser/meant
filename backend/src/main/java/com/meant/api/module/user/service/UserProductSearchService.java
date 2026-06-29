package com.meant.api.module.user.service;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.merchant.service.MerchantSemanticProductSearchService;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import com.meant.api.module.user.constant.UserProductSearchAgent;
import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.command.CurateUserProductSearchCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchCatalogInput;
import com.meant.api.module.user.service.dto.UserProductSearchCuratorResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final UserProductSearchPersistenceService userProductSearchPersistenceService;
    private final UserProductSearchEventService userProductSearchEventService;
    private final UserInventoryService userInventoryService;
    private final UserTasteProfileService userTasteProfileService;
    private final UserProductSearchCuratorService userProductSearchCuratorService;
    private final UserProductSearchProductResultMapper userProductSearchProductResultMapper;
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
            @NotNull Consumer<UserProductSearchStreamEvent> eventConsumer
    ) {
        if (!upsertCommand.id().equals(command.userId())) {
            throw UserException.forbidden("Product search user does not match authenticated user");
        }

        eventConsumer.accept(UserProductSearchStreamEvent.phase(
                UserProductSearchAgent.DISCOVERY.getValue(),
                "Understanding your request"
        ));
        String query = command.query().trim();
        UserProductSearchQueryIntentResult queryIntent = userProductSearchQueryUnderstandingService.understand(query);

        eventConsumer.accept(UserProductSearchStreamEvent.phase(
                UserProductSearchAgent.DISCOVERY.getValue(),
                "Loading your shopping context"
        ));
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
                eventConsumer.accept(UserProductSearchStreamEvent.phase(
                        UserProductSearchAgent.DISCOVERY.getValue(),
                        "Restoring cached matches"
                ));
                eventConsumer.accept(UserProductSearchStreamEvent.phase(
                        UserProductSearchAgent.CURATOR.getValue(),
                        "Restoring curator scores"
                ));
                cached.products().forEach(product -> eventConsumer.accept(
                        UserProductSearchStreamEvent.productUpdate(
                                UserProductSearchAgent.CURATOR.getValue(),
                                "Curator score restored",
                                product
                        )
                ));
                eventConsumer.accept(UserProductSearchStreamEvent.rankUpdate(
                        UserProductSearchAgent.CURATOR.getValue(),
                        "Curator order restored",
                        cached.products()
                ));
                eventConsumer.accept(UserProductSearchStreamEvent.done(cached));
                recordSearch(command, queryIntent, cached, now);
                return;
            }
        }

        eventConsumer.accept(UserProductSearchStreamEvent.phase(
                UserProductSearchAgent.DISCOVERY.getValue(),
                "Searching merchant catalogs"
        ));
        Set<String> emittedCandidateKeys = ConcurrentHashMap.newKeySet();
        List<UserProductSearchProductSnapshot> fetchedProducts = productSnapshots(
                catalogInput,
                command.merchantId(),
                fetchLimit,
                candidate -> {
                    int rank = candidate.product().rank();
                    if (rank <= offset || rank > pageEnd(offset, limit)) {
                        return;
                    }
                    if (emittedCandidateKeys.add(candidate.productKey())) {
                        eventConsumer.accept(UserProductSearchStreamEvent.product(
                                UserProductSearchAgent.DISCOVERY.getValue(),
                                "Product candidate found",
                                previewProductResult(candidate, now)
                        ));
                    } else {
                        eventConsumer.accept(UserProductSearchStreamEvent.productUpdate(
                                UserProductSearchAgent.DISCOVERY.getValue(),
                                candidate.product().detailError() == null
                                        ? "Product details updated"
                                        : "Product detail unavailable",
                                previewProductResult(candidate, now)
                        ));
                    }
                }
        );
        List<UserProductSearchProductSnapshot> products = uniqueProducts(fetchedProducts);

        eventConsumer.accept(UserProductSearchStreamEvent.phase(
                UserProductSearchAgent.CURATOR.getValue(),
                "Checking what is meant for you"
        ));
        UserProductSearchCuratorResult curated = userProductSearchCuratorService.curate(new CurateUserProductSearchCommand(
                command.userId(),
                query,
                normalizedQuery,
                profileHash,
                settings,
                tasteProfile,
                products,
                now,
                offset,
                limit
        ));
        curated.pageProducts().forEach(product -> eventConsumer.accept(UserProductSearchStreamEvent.productUpdate(
                UserProductSearchAgent.CURATOR.getValue(),
                "Curator score updated",
                product
        )));

        eventConsumer.accept(UserProductSearchStreamEvent.rankUpdate(
                UserProductSearchAgent.CURATOR.getValue(),
                "Curator order updated",
                curated.pageProducts()
        ));

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
                        curated.explanations(),
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
                        curated.pageProducts()
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
        Instant now = Instant.now();
        UserProductSearchCuratorResult curated = userProductSearchCuratorService.curate(new CurateUserProductSearchCommand(
                userId,
                query,
                normalizedQuery,
                profileHash,
                settings,
                tasteProfile,
                products,
                now,
                offset,
                limit
        ));
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
                curated.pageProducts()
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
        UserProductSearchCuratorResult curated = userProductSearchCuratorService.curate(new CurateUserProductSearchCommand(
                userId,
                query,
                normalizedQuery,
                profileHash,
                settings,
                tasteProfile,
                products,
                now,
                offset,
                limit
        ));
        return userProductSearchPersistenceService.saveSearch(
                userId,
                query,
                normalizedQuery,
                profileHash,
                userProductSearchProperties.searchVersion(),
                now,
                now.plus(userProductSearchProperties.cacheTtl()),
                products,
                curated.explanations(),
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
        return productSnapshots(catalogInput, merchantId, productLimit, null);
    }

    private List<UserProductSearchProductSnapshot> productSnapshots(
            UserProductSearchCatalogInput catalogInput,
            UUID merchantId,
            int productLimit,
            Consumer<UserProductSearchProductSnapshot> candidateConsumer
    ) {
        Consumer<MerchantSemanticProductResult> merchantCandidateConsumer = candidateConsumer == null
                ? null
                : candidate -> candidateConsumer.accept(productSnapshot(candidate));
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
                merchantCandidateConsumer
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

    private UserProductSearchProductResult previewProductResult(
            UserProductSearchProductSnapshot product,
            Instant now
    ) {
        return userProductSearchProductResultMapper.from(
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

    private int pageEnd(int offset, int limit) {
        return Math.min(offset + limit, UserProductSearchPagination.MAX_RESULT_WINDOW);
    }
}
