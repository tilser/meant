package com.meant.api.module.user.service;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.merchant.service.MerchantSemanticProductSearchService;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
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
        String profileHash = userProductSearchHashService.profileHash(settings);
        Instant now = Instant.now();

        UserProductSearchResult result;
        if (command.merchantId() != null) {
            result = searchWithoutPersisting(
                    command.userId(),
                    query,
                    catalogInput,
                    normalizedQuery,
                    profileHash,
                    settings,
                    command.merchantId()
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
                            now
                    )
                    .orElseGet(() -> searchAndPersist(
                            command.userId(),
                            query,
                            catalogInput,
                            normalizedQuery,
                            profileHash,
                            settings,
                            now
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
            UUID merchantId
    ) {
        List<UserProductSearchProductSnapshot> products = productSnapshots(catalogInput, merchantId);
        Instant now = Instant.now();
        Map<String, UserProductRecommendationExplanationResult> explanations =
                userProductRecommendationExplanationService.explain(
                        userId,
                        query,
                        normalizedQuery,
                        profileHash,
                        settings,
                        products
                );
        return new UserProductSearchResult(
                query,
                normalizedQuery,
                profileHash,
                false,
                products.stream()
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
                        .toList()
        );
    }

    private UserProductSearchResult searchAndPersist(
            UUID userId,
            String query,
            UserProductSearchCatalogInput catalogInput,
            String normalizedQuery,
            String profileHash,
            UserSettingsResult settings,
            Instant now
    ) {
        List<UserProductSearchProductSnapshot> products = productSnapshots(catalogInput, null);
        Map<String, UserProductRecommendationExplanationResult> explanations =
                userProductRecommendationExplanationService.explain(
                        userId,
                        query,
                        normalizedQuery,
                        profileHash,
                        settings,
                        products
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
                explanations
        );
    }

    private List<UserProductSearchProductSnapshot> productSnapshots(
            UserProductSearchCatalogInput catalogInput,
            UUID merchantId
    ) {
        MerchantSemanticProductSearchResult searchResult = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(
                        catalogInput.searchQuery(),
                        merchantId,
                        null,
                        null,
                        null,
                        null,
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
}
