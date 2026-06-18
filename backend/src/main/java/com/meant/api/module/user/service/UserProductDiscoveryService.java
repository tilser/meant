package com.meant.api.module.user.service;

import com.meant.api.common.properties.OpenRouterProperties;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.command.UpsertUserCommand;
import com.meant.api.module.user.service.dto.UserProductDiscoveryResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.query.GetUserProductDiscoveryQuery;
import com.meant.api.module.user.service.query.ListSavedProductsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserProductDiscoveryService {

    private final UserSettingsService userSettingsService;
    private final UserSavedProductService userSavedProductService;
    private final UserProductSearchHashService userProductSearchHashService;
    private final UserProductSearchPersistenceService userProductSearchPersistenceService;
    private final UserProductSearchProperties userProductSearchProperties;
    private final OpenRouterProperties openRouterProperties;

    public UserProductDiscoveryResult get(
            @NotNull @Valid UpsertUserCommand upsertCommand,
            @NotNull @Valid GetUserProductDiscoveryQuery query
    ) {
        validateUser(upsertCommand, query.userId());
        UserSettingsResult settings = userSettingsService.get(upsertCommand);
        String profileHash = userProductSearchHashService.profileHash(settings);
        List<UserSavedProductResult> savedProducts = userSavedProductService.list(
                upsertCommand,
                new ListSavedProductsQuery(query.userId()));
        Set<String> savedProductKeys = savedProducts.stream()
                .map(UserSavedProductResult::id)
                .collect(Collectors.toSet());
        List<UserProductSearchProductResult> recentProducts = userProductSearchPersistenceService.findRecentProducts(
                        query.userId(),
                        profileHash,
                        userProductSearchProperties.searchVersion(),
                        openRouterProperties.models().productRecommendationExplainer(),
                        userProductSearchProperties.explanationPromptVersion(),
                        Instant.now(),
                        userProductSearchProperties.discoveryRecentSearchLimit(),
                        userProductSearchProperties.discoveryRecentProductLimit()
                )
                .stream()
                .filter(product -> !savedProductKeys.contains(product.productKey()))
                .toList();

        return new UserProductDiscoveryResult(savedProducts, recentProducts);
    }

    private void validateUser(UpsertUserCommand upsertCommand, UUID userId) {
        if (!upsertCommand.id().equals(userId)) {
            throw UserException.forbidden("Product discovery user does not match authenticated user");
        }
    }
}
