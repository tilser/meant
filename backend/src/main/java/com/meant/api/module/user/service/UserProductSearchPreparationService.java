package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserProductSearchCatalogInput;
import com.meant.api.module.user.service.dto.UserProductSearchPreparation;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Builds the shared user-aware input used by flat and federated discovery. */
@Service
@RequiredArgsConstructor
public class UserProductSearchPreparationService {

    private final UserProductSearchQueryUnderstandingService queryUnderstandingService;
    private final UserSettingsService userSettingsService;
    private final UserTasteProfileService userTasteProfileService;
    private final UserProductSearchCatalogInputBuilder catalogInputBuilder;
    private final UserProductSearchHashService hashService;
    private final UserInventoryService userInventoryService;
    private final UserProductSearchProfileSuppressionPolicy profileSuppressionPolicy;

    public UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command
    ) {
        return prepare(profileCommand, command, null, Set.of(), Set.of(), ignored -> { });
    }

    public UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters
    ) {
        return prepare(
                profileCommand,
                command,
                discoveryFilters,
                Set.of(),
                Set.of(),
                ignored -> { }
        );
    }

    public UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets
    ) {
        return prepare(
                profileCommand,
                command,
                discoveryFilters,
                explicitAnyTargets,
                explicitAnyTargets,
                ignored -> { }
        );
    }

    public UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets
    ) {
        return prepare(
                profileCommand,
                command,
                discoveryFilters,
                explicitAnyTargets,
                profileSuppressionTargets,
                ignored -> { }
        );
    }

    public UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            Consumer<Stage> stageConsumer
    ) {
        return prepare(profileCommand, command, null, Set.of(), Set.of(), stageConsumer);
    }

    public UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            Consumer<Stage> stageConsumer
    ) {
        return prepare(
                profileCommand,
                command,
                discoveryFilters,
                Set.of(),
                Set.of(),
                stageConsumer,
                false
        );
    }

    public UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets,
            Consumer<Stage> stageConsumer
    ) {
        return prepare(
                profileCommand,
                command,
                discoveryFilters,
                explicitAnyTargets,
                explicitAnyTargets,
                stageConsumer
        );
    }

    public UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets,
            Consumer<Stage> stageConsumer
    ) {
        return prepare(
                profileCommand,
                command,
                discoveryFilters,
                explicitAnyTargets,
                profileSuppressionTargets,
                stageConsumer,
                false
        );
    }

    public UserProductSearchPreparation prepareSimilarity(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters
    ) {
        return prepare(
                profileCommand,
                command,
                discoveryFilters,
                Set.of(),
                Set.of(),
                ignored -> { },
                true
        );
    }

    public UserProductSearchPreparation prepareSimilarity(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets
    ) {
        return prepareSimilarity(
                profileCommand,
                command,
                discoveryFilters,
                explicitAnyTargets,
                explicitAnyTargets
        );
    }

    public UserProductSearchPreparation prepareSimilarity(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets
    ) {
        return prepare(
                profileCommand,
                command,
                discoveryFilters,
                explicitAnyTargets,
                profileSuppressionTargets,
                ignored -> { },
                true
        );
    }

    private UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            CatalogDiscoveryFilters discoveryFilters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets,
            Consumer<Stage> stageConsumer,
            boolean similaritySearch
    ) {
        if (!profileCommand.id().equals(command.userId())) {
            throw UserException.forbidden("Product search user does not match authenticated user");
        }
        stageConsumer.accept(Stage.UNDERSTANDING_REQUEST);
        String query = command.query().trim();
        UserProductSearchQueryIntentResult queryIntent = similaritySearch
                ? queryUnderstandingService.understandSimilarity(query)
                : discoveryFilters == null
                ? queryUnderstandingService.understand(query)
                : queryUnderstandingService.understandQualified(query);

        stageConsumer.accept(Stage.LOADING_CONTEXT);
        Set<UserProductSearchQuestionTarget> requestExplicitAnyTargets =
                explicitAnyTargets == null ? Set.of() : Set.copyOf(explicitAnyTargets);
        Set<UserProductSearchQuestionTarget> requestProfileSuppressionTargets =
                profileSuppressionTargets == null
                        ? Set.of()
                        : Set.copyOf(profileSuppressionTargets);
        UserSettingsResult persistedSettings = userSettingsService.get(profileCommand);
        UserTasteProfileResult persistedTasteProfile =
                userTasteProfileService.profile(command.userId(), persistedSettings);
        UserSettingsResult settings =
                profileSuppressionPolicy.settings(
                        persistedSettings,
                        requestProfileSuppressionTargets
                );
        UserTasteProfileResult tasteProfile =
                profileSuppressionPolicy.tasteProfile(
                        persistedTasteProfile,
                        requestProfileSuppressionTargets
                );
        UserProductSearchCatalogInput catalogInput = catalogInputBuilder.build(
                query,
                queryIntent,
                settings,
                command.buyerIp(),
                command.userAgent(),
                command.language(),
                discoveryFilters
        );
        int offset = command.offset();
        int limit = command.limit();
        return new UserProductSearchPreparation(
                query,
                queryIntent,
                settings,
                tasteProfile,
                catalogInput,
                catalogInput.cacheKey(),
                hashService.searchProfileHash(
                        settings,
                        userInventoryService.inventoryProfileHash(command.userId()),
                        tasteProfile.profileHash()
                ),
                Instant.now(),
                offset,
                limit,
                fetchLimit(offset, limit),
                requestExplicitAnyTargets,
                requestProfileSuppressionTargets
        );
    }

    private int fetchLimit(int offset, int limit) {
        int pageEnd = Math.min(offset + limit, UserProductSearchPagination.MAX_RESULT_WINDOW);
        return pageEnd >= UserProductSearchPagination.MAX_RESULT_WINDOW
                ? UserProductSearchPagination.MAX_RESULT_WINDOW
                : pageEnd + 1;
    }

    public enum Stage {
        UNDERSTANDING_REQUEST,
        LOADING_CONTEXT
    }
}
