package com.meant.api.module.user.service;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.merchant.service.MerchantSemanticProductSearchService;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticProductSearchResult;
import com.meant.api.module.merchant.service.query.SemanticProductSearchQuery;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
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
    private final UserProductSearchHashService userProductSearchHashService;
    private final UserProductRecommendationExplanationService userProductRecommendationExplanationService;
    private final UserProductSearchPersistenceService userProductSearchPersistenceService;
    private final UserProductSearchProperties userProductSearchProperties;
    private final OpenRouterProperties openRouterProperties;

    public UserProductSearchResult search(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid SearchUserProductsCommand command
    ) {
        if (!upsertCommand.id().equals(command.userId())) {
            throw new UserException("Product search user does not match authenticated user");
        }

        String query = command.query().trim();
        UserSettingsResult settings = userSettingsService.get(upsertCommand);
        String normalizedQuery = userProductSearchHashService.normalizeQuery(query);
        String profileHash = userProductSearchHashService.profileHash(settings);
        Instant now = Instant.now();

        return userProductSearchPersistenceService.findCachedSearch(
                        command.userId(),
                        query,
                        normalizedQuery,
                        profileHash,
                        userProductSearchProperties.searchVersion(),
                        openRouterProperties.models().productRecommendationExplainer(),
                        userProductSearchProperties.explanationPromptVersion(),
                        now
                )
                .orElseGet(() -> searchAndPersist(command.userId(), query, normalizedQuery, profileHash, settings, now));
    }

    private UserProductSearchResult searchAndPersist(
            UUID userId,
            String query,
            String normalizedQuery,
            String profileHash,
            UserSettingsResult settings,
            Instant now
    ) {
        MerchantSemanticProductSearchResult searchResult = merchantSemanticProductSearchService.search(
                new SemanticProductSearchQuery(query, null, null, null, null)
        );
        List<UserProductSearchProductSnapshot> products = safeProducts(searchResult).stream()
                .map(product -> new UserProductSearchProductSnapshot(
                        userProductSearchHashService.productKey(product),
                        userProductSearchHashService.productHash(product),
                        product
                ))
                .toList();
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

    private List<MerchantSemanticProductResult> safeProducts(
            MerchantSemanticProductSearchResult searchResult
    ) {
        return searchResult == null || searchResult.products() == null ? List.of() : searchResult.products();
    }
}
