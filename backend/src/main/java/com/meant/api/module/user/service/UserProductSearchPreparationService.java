package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserProductSearchCatalogInput;
import com.meant.api.module.user.service.dto.UserProductSearchPreparation;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import java.time.Instant;
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

    public UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command
    ) {
        return prepare(profileCommand, command, ignored -> { });
    }

    public UserProductSearchPreparation prepare(
            EnsureUserProfileCommand profileCommand,
            SearchUserProductsCommand command,
            Consumer<Stage> stageConsumer
    ) {
        if (!profileCommand.id().equals(command.userId())) {
            throw UserException.forbidden("Product search user does not match authenticated user");
        }
        stageConsumer.accept(Stage.UNDERSTANDING_REQUEST);
        String query = command.query().trim();
        UserProductSearchQueryIntentResult queryIntent = queryUnderstandingService.understand(query);

        stageConsumer.accept(Stage.LOADING_CONTEXT);
        UserSettingsResult settings = userSettingsService.get(profileCommand);
        UserTasteProfileResult tasteProfile = userTasteProfileService.profile(command.userId(), settings);
        UserProductSearchCatalogInput catalogInput = catalogInputBuilder.build(
                query,
                queryIntent,
                settings,
                command.buyerIp(),
                command.userAgent()
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
                fetchLimit(offset, limit)
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
