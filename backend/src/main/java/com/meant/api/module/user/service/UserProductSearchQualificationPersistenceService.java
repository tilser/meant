package com.meant.api.module.user.service;

import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.entity.UserProductSearchQualification;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.repository.UserProductSearchQualificationRepository;
import com.meant.api.module.user.service.command.PersistUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.SaveUserProductSearchPreferencesCommand;
import com.meant.api.module.user.service.command.UserProductSearchPreferenceCommand;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class UserProductSearchQualificationPersistenceService {

    private final UserProductSearchQualificationRepository qualificationRepository;
    private final UserProductSearchQualificationPlanCodec planCodec;
    private final UserProductSearchPreferenceService preferenceService;

    @Transactional(readOnly = true)
    public Optional<UserProductSearchQualificationSnapshot> find(
            @NotNull @Valid GetUserProductSearchQualificationQuery query
    ) {
        return qualificationRepository.findByIdAndUserId(query.qualificationId(), query.userId())
                .map(this::snapshot);
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
    public UserProductSearchQualificationSnapshot persist(
            @NotNull @Valid PersistUserProductSearchQualificationCommand command
    ) {
        String planJson = planCodec.encode(command.plan());
        Instant now = Instant.now();
        UserProductSearchQualification qualification = qualificationRepository
                .findByIdForUpdate(command.qualificationId())
                .map(existing -> updateOrReturnReady(existing, command, planJson, now))
                .orElseGet(() -> create(command, planJson, now));
        return snapshot(qualificationRepository.save(qualification));
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
        applyDurablePreferences(command.userId(), command.plan());
        return qualification;
    }

    private UserProductSearchQualification updateOrReturnReady(
            UserProductSearchQualification existing,
            PersistUserProductSearchQualificationCommand command,
            String planJson,
            Instant now
    ) {
        if (!existing.getUserId().equals(command.userId())
                || !existing.getConversationId().equals(command.conversationId())
                || !java.util.Objects.equals(existing.getMerchantId(), command.merchantId())) {
            throw UserException.notFound("Product-search qualification not found");
        }
        if (existing.getStatus() == UserProductSearchQualificationStatus.READY) {
            return existing;
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
        applyDurablePreferences(command.userId(), command.plan());
        return existing;
    }

    private void applyDurablePreferences(UUID userId, UserProductSearchQualificationPlan plan) {
        if (plan.durableAttributes().isEmpty()) {
            return;
        }
        preferenceService.upsert(new SaveUserProductSearchPreferencesCommand(
                userId,
                plan.durableAttributes().stream()
                        .map(attribute -> new UserProductSearchPreferenceCommand(
                                attribute.scope(), attribute.name(), attribute.values()))
                        .toList()
        ));
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
