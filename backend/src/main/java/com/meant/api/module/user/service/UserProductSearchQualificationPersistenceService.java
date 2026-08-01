package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.entity.UserProductSearchQualification;
import com.meant.api.module.user.entity.UserProductSearchQualificationRequest;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserProductSearchQualificationRepository;
import com.meant.api.module.user.repository.UserProductSearchQualificationRequestRepository;
import com.meant.api.module.user.service.command.CancelUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.PersistUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import com.meant.api.module.user.service.query.FindPendingUserProductSearchQualificationQuery;
import com.meant.api.module.user.service.query.FindUserProductSearchQualificationByRequestQuery;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserProductSearchQualificationPersistenceService {

    private final UserProductSearchQualificationRepository qualificationRepository;
    private final UserProductSearchQualificationRequestRepository requestRepository;
    private final UserProductSearchQualificationPlanCodec planCodec;

    @Transactional(readOnly = true)
    public Optional<UserProductSearchQualificationSnapshot> find(
            @NotNull @Valid GetUserProductSearchQualificationQuery query
    ) {
        return qualificationRepository.findByIdAndUserId(query.qualificationId(), query.userId())
                .map(this::snapshot);
    }

    @Transactional(readOnly = true)
    public Optional<UserProductSearchQualificationSnapshot> findLatestPending(
            @NotNull @Valid FindPendingUserProductSearchQualificationQuery query
    ) {
        return qualificationRepository
                .findFirstByUserIdAndConversationIdAndMerchantIdAndStatusOrderByUpdatedAtDesc(
                        query.userId(),
                        query.conversationId(),
                        query.merchantId(),
                        UserProductSearchQualificationStatus.NEEDS_INPUT
                )
                .map(this::snapshot);
    }

    @Transactional(readOnly = true)
    public Optional<UserProductSearchQualificationSnapshot> findByRequest(
            @NotNull @Valid FindUserProductSearchQualificationByRequestQuery query
    ) {
        return requestRepository.findById(query.requestId())
                .map(request -> {
                    validateRequestIdentity(request, query);
                    return qualificationRepository
                            .findByIdAndUserId(request.getQualificationId(), query.userId())
                            .filter(qualification -> qualification.getConversationId()
                                    .equals(query.conversationId()))
                            .filter(qualification -> java.util.Objects.equals(
                                    qualification.getMerchantId(), query.merchantId()))
                            .map(this::snapshot)
                            .orElseThrow(() -> UserException.notFound(
                                    "Product-search qualification request was not found"));
                });
    }

    @Transactional(readOnly = true)
    public UserProductSearchQualificationSnapshot getReady(
            @NotNull @Valid GetUserProductSearchQualificationQuery query
    ) {
        UserProductSearchQualificationSnapshot snapshot = find(query)
                .orElseThrow(() -> UserException.notFound("Product-search qualification not found"));
        if (snapshot.status() != UserProductSearchQualificationStatus.READY) {
            throw new UserException("Product-search qualification still needs input");
        }
        return snapshot;
    }

    @Transactional
    public UserProductSearchQualificationSnapshot refreshReady(
            @NotNull @Valid GetUserProductSearchQualificationQuery query
    ) {
        UserProductSearchQualification qualification = qualificationRepository
                .findByIdForUpdate(query.qualificationId())
                .filter(existing -> existing.getUserId().equals(query.userId()))
                .orElseThrow(() -> UserException.notFound("Product-search qualification not found"));
        if (qualification.getStatus() != UserProductSearchQualificationStatus.READY) {
            throw new UserException("Product-search qualification still needs input");
        }
        qualification.refreshReady(Instant.now());
        return snapshot(qualificationRepository.save(qualification));
    }

    @Transactional
    public UserProductSearchQualificationSnapshot cancel(
            @NotNull @Valid CancelUserProductSearchQualificationCommand command
    ) {
        UserProductSearchQualification qualification = qualificationRepository
                .findByIdForUpdate(command.qualificationId())
                .filter(existing -> existing.getUserId().equals(command.userId()))
                .filter(existing -> existing.getConversationId().equals(command.conversationId()))
                .filter(existing -> java.util.Objects.equals(existing.getMerchantId(), command.merchantId()))
                .orElseThrow(() -> UserException.notFound("Product-search qualification not found"));
        if (qualification.getStatus() != UserProductSearchQualificationStatus.NEEDS_INPUT
                || !qualification.getUpdatedAt().equals(command.expectedUpdatedAt())) {
            throw UserException.conflict("Product-search qualification changed before it could be cancelled");
        }
        qualification.cancel(Instant.now());
        return snapshot(qualificationRepository.save(qualification));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserProductSearchQualificationSnapshot persist(
            @NotNull @Valid PersistUserProductSearchQualificationCommand command
    ) {
        String planJson = planCodec.encode(command.plan());
        Instant now = Instant.now();
        UserProductSearchQualification qualification = qualificationRepository
                .findByIdForUpdate(command.qualificationId())
                .map(existing -> {
                    validateQualificationScope(existing, command);
                    return findBoundRequestForUpdate(command)
                            .map(request -> {
                                validateRequestIdentity(request, command);
                                if (existing.getStatus() == UserProductSearchQualificationStatus.CANCELLED) {
                                    throw UserException.conflict(
                                            "Product-search qualification request was already cancelled");
                                }
                                return existing;
                            })
                            .orElseGet(() -> updateOrReturnReady(existing, command, planJson, now));
                })
                .orElseGet(() -> create(command, planJson, now));
        UserProductSearchQualification saved = qualificationRepository.save(qualification);
        bindRequest(command, now);
        return snapshot(saved);
    }

    /**
     * Reconciles the loser of a concurrent first insert after its original transaction rolled back.
     *
     * <p>This method must be invoked through the Spring proxy after the write exception escaped the
     * failed transaction. It never performs or wraps remote qualification work.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<UserProductSearchQualificationSnapshot> reconcileConcurrentRequest(
            @NotNull @Valid PersistUserProductSearchQualificationCommand command
    ) {
        if (command.requestId() == null) {
            return Optional.empty();
        }
        return requestRepository.findById(command.requestId())
                .map(request -> {
                    validateRequestIdentity(request, command);
                    return qualificationRepository
                            .findByIdAndUserId(request.getQualificationId(), command.userId())
                            .filter(qualification -> qualification.getConversationId()
                                    .equals(command.conversationId()))
                            .filter(qualification -> java.util.Objects.equals(
                                    qualification.getMerchantId(), command.merchantId()))
                            .map(this::snapshot)
                            .orElseThrow(() -> UserException.notFound(
                                    "Product-search qualification request was not found"));
                });
    }

    private Optional<UserProductSearchQualificationRequest> findBoundRequestForUpdate(
            PersistUserProductSearchQualificationCommand command
    ) {
        return command.requestId() == null
                ? Optional.empty()
                : requestRepository.findByIdForUpdate(command.requestId());
    }

    private void bindRequest(PersistUserProductSearchQualificationCommand command, Instant now) {
        if (command.requestId() == null) {
            return;
        }
        UserProductSearchQualificationRequest existing =
                requestRepository.findByIdForUpdate(command.requestId()).orElse(null);
        if (existing != null) {
            validateRequestIdentity(existing, command);
            return;
        }
        requestRepository.save(UserProductSearchQualificationRequest.create(
                command.requestId(),
                command.qualificationId(),
                command.userId(),
                command.conversationId(),
                command.merchantId(),
                command.requestMessage(),
                now
        ));
    }

    private void validateRequestIdentity(
            UserProductSearchQualificationRequest request,
            FindUserProductSearchQualificationByRequestQuery query
    ) {
        if (!request.getUserId().equals(query.userId())
                || !request.getConversationId().equals(query.conversationId())
                || !java.util.Objects.equals(request.getMerchantId(), query.merchantId())
                || !request.getMessage().equals(query.message().trim())) {
            throw UserException.conflict("Product-search qualification request identity was already used");
        }
    }

    private void validateRequestIdentity(
            UserProductSearchQualificationRequest request,
            PersistUserProductSearchQualificationCommand command
    ) {
        if (!request.getQualificationId().equals(command.qualificationId())
                || !request.getUserId().equals(command.userId())
                || !request.getConversationId().equals(command.conversationId())
                || !java.util.Objects.equals(request.getMerchantId(), command.merchantId())
                || !request.getMessage().equals(command.requestMessage())) {
            throw UserException.conflict("Product-search qualification request identity was already used");
        }
    }

    private UserProductSearchQualification create(
            PersistUserProductSearchQualificationCommand command,
            String planJson,
            Instant now
    ) {
        if (command.expectedUpdatedAt() != null) {
            throw UserException.conflict("Product-search qualification changed before it could be updated");
        }
        UserProductSearchQualification qualification = UserProductSearchQualification.create(
                command.qualificationId(),
                command.userId(),
                command.conversationId(),
                command.merchantId(),
                command.originalQuery().trim(),
                command.status(),
                planJson,
                command.model().trim(),
                command.promptVersion().trim(),
                now
        );
        return qualification;
    }

    private UserProductSearchQualification updateOrReturnReady(
            UserProductSearchQualification existing,
            PersistUserProductSearchQualificationCommand command,
            String planJson,
            Instant now
    ) {
        if (existing.getStatus() == UserProductSearchQualificationStatus.READY) {
            throw UserException.conflict(
                    "Product-search qualification changed before this request could be bound");
        }
        if (command.expectedUpdatedAt() == null
                || !existing.getUpdatedAt().equals(command.expectedUpdatedAt())) {
            throw UserException.conflict("Product-search qualification changed before it could be updated");
        }
        existing.updatePending(
                command.status(),
                planJson,
                command.model().trim(),
                command.promptVersion().trim(),
                now
        );
        return existing;
    }

    private void validateQualificationScope(
            UserProductSearchQualification qualification,
            PersistUserProductSearchQualificationCommand command
    ) {
        if (!qualification.getUserId().equals(command.userId())
                || !qualification.getConversationId().equals(command.conversationId())
                || !java.util.Objects.equals(qualification.getMerchantId(), command.merchantId())) {
            throw UserException.notFound("Product-search qualification not found");
        }
    }

    private UserProductSearchQualificationSnapshot snapshot(UserProductSearchQualification qualification) {
        return new UserProductSearchQualificationSnapshot(
                qualification.getId(),
                qualification.getUserId(),
                qualification.getConversationId(),
                qualification.getMerchantId(),
                qualification.getOriginalQuery(),
                qualification.getStatus(),
                planCodec.decode(qualification.getPlanJson()),
                qualification.getModel(),
                qualification.getPromptVersion(),
                qualification.getCreatedAt(),
                qualification.getUpdatedAt()
        );
    }
}
