package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.PersistUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationModelResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import com.meant.api.module.user.service.query.GetUserDiscoverConversationQuery;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/** Orchestrates remote qualification without holding a database transaction around the LLM call. */
@Service
@Validated
@RequiredArgsConstructor
public class UserProductSearchQualificationService {

    private final UserSettingsService userSettingsService;
    private final UserProductSearchPreferenceService preferenceService;
    private final UserProductSearchQualificationModelService modelService;
    private final UserProductSearchQualificationPersistenceService persistenceService;
    private final UserProductSearchCatalogInputBuilder catalogInputBuilder;
    private final UserDiscoverConversationService conversationService;

    public UserProductSearchQualificationResult qualify(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid QualifyUserProductSearchCommand command
    ) {
        if (!profileCommand.id().equals(command.userId())) {
            throw UserException.forbidden("Product-search qualification user does not match authenticated user");
        }
        if (command.merchantId() != null) {
            throw new UserException(
                    "Merchant-scoped search is unavailable until the merchant has a trusted Shopify Shop GID"
            );
        }
        conversationService.requireOwned(new GetUserDiscoverConversationQuery(
                command.userId(), command.conversationId()));

        UserProductSearchQualificationSnapshot previous = previous(command);
        if (previous != null && previous.status() == UserProductSearchQualificationStatus.READY) {
            if (!previous.plan().currentSchema()) {
                throw UserException.notFound(
                        "Product-search qualification uses an outdated plan; start a new qualification");
            }
            return result(persistenceService.refreshReady(new GetUserProductSearchQualificationQuery(
                    command.userId(), previous.qualificationId())));
        }
        String originalQuery = previous == null ? command.message().trim() : previous.originalQuery();
        catalogInputBuilder.validateSupportedCurrency(command.message());
        UUID merchantId = previous == null ? command.merchantId() : previous.merchantId();
        var durablePreferences = preferenceService.list(command.userId());
        UserProductSearchQualificationModelResult generated = modelService.generate(
                new GenerateUserProductSearchQualificationQuery(
                        originalQuery,
                        command.message().trim(),
                        previous == null ? null : previous.plan(),
                        userSettingsService.get(profileCommand),
                        durablePreferences
                )
        );
        boolean ready = generated.plan().currentSchema()
                && generated.plan().missingFilters().isEmpty()
                && generated.plan().missingTargets().isEmpty();
        UserProductSearchQualificationStatus status = ready
                ? UserProductSearchQualificationStatus.READY
                : UserProductSearchQualificationStatus.NEEDS_INPUT;
        UUID qualificationId = previous == null ? UUID.randomUUID() : previous.qualificationId();
        UserProductSearchQualificationSnapshot persisted = persistenceService.persist(
                new PersistUserProductSearchQualificationCommand(
                        qualificationId,
                        command.userId(),
                        command.conversationId(),
                        merchantId,
                        originalQuery,
                        previous == null ? null : previous.updatedAt(),
                        status,
                        generated.plan(),
                        generated.model(),
                        generated.promptVersion()
                )
        );
        return result(persisted);
    }

    /** Authoritative accessor for later catalog-search integration. READY plans are immutable. */
    public UserProductSearchQualificationSnapshot getReady(
            @NotNull @Valid GetUserProductSearchQualificationQuery query
    ) {
        return persistenceService.getReady(query);
    }

    private UserProductSearchQualificationSnapshot previous(QualifyUserProductSearchCommand command) {
        if (command.qualificationId() == null) {
            return null;
        }
        UserProductSearchQualificationSnapshot snapshot = persistenceService.find(
                        new GetUserProductSearchQualificationQuery(command.userId(), command.qualificationId()))
                .orElseThrow(() -> UserException.notFound("Product-search qualification not found"));
        if (!snapshot.conversationId().equals(command.conversationId())
                || !Objects.equals(snapshot.merchantId(), command.merchantId())) {
            throw UserException.notFound("Product-search qualification not found");
        }
        return snapshot;
    }

    private UserProductSearchQualificationResult result(UserProductSearchQualificationSnapshot snapshot) {
        return new UserProductSearchQualificationResult(
                snapshot.qualificationId(),
                snapshot.status(),
                snapshot.plan().assistantMessage(),
                snapshot.plan().suggestedReplies(),
                snapshot.plan().missingFilters(),
                snapshot.plan().effectiveQuery()
        );
    }

}
