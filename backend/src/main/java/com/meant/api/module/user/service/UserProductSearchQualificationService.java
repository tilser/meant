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
import com.meant.api.module.user.service.query.FindUserProductSearchQualificationByRequestQuery;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/** Orchestrates remote qualification without holding a database transaction around the LLM call. */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class UserProductSearchQualificationService {

    private final UserSettingsService userSettingsService;
    private final UserTasteProfileService userTasteProfileService;
    private final UserProductSearchPreferenceService preferenceService;
    private final UserProductSearchQualificationModelService modelService;
    private final UserProductSearchQualificationPersistenceService persistenceService;
    private final UserProductSearchCatalogInputBuilder catalogInputBuilder;

    public UserProductSearchQualificationResult qualify(
            @NotNull @Valid EnsureUserProfileCommand profileCommand,
            @NotNull @Valid QualifyUserProductSearchCommand command
    ) {
        log.info(
                "Product-search qualification orchestration started. userId={}, conversationId={}, "
                        + "requestedQualificationId={}, merchantScoped={}",
                command.userId(),
                command.conversationId(),
                command.qualificationId(),
                command.merchantId() != null
        );
        if (!profileCommand.id().equals(command.userId())) {
            throw UserException.forbidden("Product-search qualification user does not match authenticated user");
        }
        UserProductSearchQualificationSnapshot replay = requestReplay(command);
        if (replay == null) {
            replay = idempotentStart(command);
        }
        if (replay != null) {
            if (!replay.plan().currentSchema()) {
                throw UserException.notFound(
                        "Product-search qualification uses an outdated plan; start a new qualification");
            }
            return replay.status() == UserProductSearchQualificationStatus.READY
                    ? result(persistenceService.refreshReady(new GetUserProductSearchQualificationQuery(
                            command.userId(), replay.qualificationId())))
                    : result(replay);
        }
        UserProductSearchQualificationSnapshot previous = previous(command);
        log.info(
                "Product-search qualification previous state loaded. userId={}, conversationId={}, "
                        + "requestedQualificationId={}, previousStatus={}",
                command.userId(),
                command.conversationId(),
                command.qualificationId(),
                previous == null ? "NONE" : previous.status()
        );
        if (previous != null && previous.status() == UserProductSearchQualificationStatus.READY) {
            if (!previous.plan().currentSchema()) {
                throw UserException.notFound(
                        "Product-search qualification uses an outdated plan; start a new qualification");
            }
            return result(persistenceService.refreshReady(new GetUserProductSearchQualificationQuery(
                    command.userId(), previous.qualificationId())));
        }
        String originalQuery = previous == null ? command.message().trim() : previous.originalQuery();
        UUID merchantId = previous == null ? command.merchantId() : previous.merchantId();
        var durablePreferences = preferenceService.list(command.userId());
        var settings = userSettingsService.get(profileCommand);
        catalogInputBuilder.validateSupportedCurrency(command.message(), settings.currency());
        var tasteProfile = userTasteProfileService.profile(command.userId(), settings);
        log.info(
                "Product-search qualification invoking model. userId={}, conversationId={}, "
                        + "qualificationId={}, durablePreferenceCount={}, conversationMessageCount={}, "
                        + "tasteSignalCount={}",
                command.userId(),
                command.conversationId(),
                command.qualificationId(),
                durablePreferences.size(),
                command.conversation().size(),
                tasteProfile.signals().size()
        );
        UserProductSearchQualificationModelResult generated = modelService.generate(
                new GenerateUserProductSearchQualificationQuery(
                        originalQuery,
                        command.message().trim(),
                        previous == null ? null : previous.plan(),
                        settings,
                        durablePreferences,
                        command.conversation(),
                        tasteProfile,
                        command.trustedReferenceProductText()
                )
        );
        boolean ready = generated.plan().currentSchema()
                && generated.plan().missingFilters().isEmpty()
                && generated.plan().missingTargets().isEmpty();
        UserProductSearchQualificationStatus status = ready
                ? UserProductSearchQualificationStatus.READY
                : UserProductSearchQualificationStatus.NEEDS_INPUT;
        log.info(
                "Product-search qualification model result accepted. userId={}, conversationId={}, "
                        + "status={}, currentSchema={}, missingFilters={}, missingTargets={}",
                command.userId(),
                command.conversationId(),
                status,
                generated.plan().currentSchema(),
                generated.plan().missingFilters(),
                generated.plan().missingTargets()
        );
        UUID qualificationId = previous == null
                ? startingQualificationId(command)
                : previous.qualificationId();
        PersistUserProductSearchQualificationCommand persistCommand =
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
                generated.promptVersion(),
                command.requestId(),
                command.requestId() == null ? null : command.message()
        );
        UserProductSearchQualificationSnapshot persisted;
        try {
            persisted = persistenceService.persist(persistCommand);
        } catch (DataIntegrityViolationException concurrentInsert) {
            persisted = reconcileConcurrentRequest(persistCommand, concurrentInsert);
        }
        log.info(
                "Product-search qualification persisted. userId={}, conversationId={}, qualificationId={}, status={}",
                command.userId(),
                command.conversationId(),
                persisted.qualificationId(),
                persisted.status()
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
        if (command.expectedQualificationUpdatedAt() != null
                && (snapshot.status() != UserProductSearchQualificationStatus.NEEDS_INPUT
                        || !snapshot.updatedAt().equals(command.expectedQualificationUpdatedAt()))) {
            throw UserException.conflict(
                    "Product-search qualification changed before this answer could be applied");
        }
        if (snapshot.status() == UserProductSearchQualificationStatus.CANCELLED) {
            throw UserException.notFound("Product-search qualification was cancelled");
        }
        return snapshot;
    }

    private UserProductSearchQualificationSnapshot reconcileConcurrentRequest(
            PersistUserProductSearchQualificationCommand command,
            DataIntegrityViolationException concurrentInsert
    ) {
        UserProductSearchQualificationSnapshot replay = persistenceService
                .reconcileConcurrentRequest(command)
                .orElseThrow(() -> concurrentInsert);
        if (replay.status() == UserProductSearchQualificationStatus.CANCELLED) {
            throw UserException.conflict("Product-search qualification request was already cancelled");
        }
        if (!replay.plan().currentSchema()) {
            throw UserException.conflict(
                    "Product-search qualification request resolved to an outdated plan");
        }
        return replay;
    }

    private UserProductSearchQualificationSnapshot idempotentStart(
            QualifyUserProductSearchCommand command
    ) {
        if (command.qualificationId() != null || command.requestId() == null) {
            return null;
        }
        UUID qualificationId = startingQualificationId(command);
        UserProductSearchQualificationSnapshot snapshot = persistenceService.find(
                        new GetUserProductSearchQualificationQuery(command.userId(), qualificationId))
                .orElse(null);
        if (snapshot == null) {
            return null;
        }
        if (!snapshot.conversationId().equals(command.conversationId())
                || !Objects.equals(snapshot.merchantId(), command.merchantId())
                || !snapshot.originalQuery().equals(command.message().trim())) {
            throw UserException.conflict("Product-search qualification request identity was already used");
        }
        if (snapshot.status() == UserProductSearchQualificationStatus.CANCELLED) {
            throw UserException.conflict("Product-search qualification request was already cancelled");
        }
        return snapshot;
    }

    private UserProductSearchQualificationSnapshot requestReplay(
            QualifyUserProductSearchCommand command
    ) {
        if (command.requestId() == null) {
            return null;
        }
        UserProductSearchQualificationSnapshot snapshot = persistenceService.findByRequest(
                        new FindUserProductSearchQualificationByRequestQuery(
                                command.userId(),
                                command.conversationId(),
                                command.merchantId(),
                                command.requestId(),
                                command.message()
                        ))
                .orElse(null);
        if (snapshot == null) {
            return null;
        }
        if (command.qualificationId() != null
                && !command.qualificationId().equals(snapshot.qualificationId())) {
            throw UserException.conflict("Product-search qualification request identity was already used");
        }
        if (snapshot.status() == UserProductSearchQualificationStatus.CANCELLED) {
            throw UserException.conflict("Product-search qualification request was already cancelled");
        }
        return snapshot;
    }

    private UUID startingQualificationId(QualifyUserProductSearchCommand command) {
        UUID requestQualificationId = command.requestQualificationId();
        return requestQualificationId == null ? UUID.randomUUID() : requestQualificationId;
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
