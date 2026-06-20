package com.meant.api.module.user.entity;

import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "user_taste_signals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_taste_signals_user_signal",
                        columnNames = {"user_id", "signal_type", "signal_key"}
                )
        }
)
public class UserTasteSignal {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserTasteSignalType signalType;

    @Column(nullable = false)
    private String signalKey;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private double weight;

    @Column(nullable = false)
    private int positiveCount;

    @Column(nullable = false)
    private int negativeCount;

    @Column(nullable = false)
    private String lastBehavior;

    private String suggestedFilterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserTasteSuggestionStatus suggestionStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserTasteSignalStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static UserTasteSignal create(
            UUID userId,
            UserTasteSignalType signalType,
            String signalKey,
            String label,
            String lastBehavior,
            double weightDelta,
            String suggestedFilterId,
            Instant now
    ) {
        return UserTasteSignal.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .signalType(signalType)
                .signalKey(signalKey)
                .label(label)
                .weight(clampedWeight(weightDelta))
                .positiveCount(weightDelta > 0 ? 1 : 0)
                .negativeCount(weightDelta < 0 ? 1 : 0)
                .lastBehavior(lastBehavior)
                .suggestedFilterId(suggestedFilterId)
                .suggestionStatus(UserTasteSuggestionStatus.PENDING)
                .status(UserTasteSignalStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public UserTasteSignal reinforce(
            String nextLabel,
            String behavior,
            double weightDelta,
            String nextSuggestedFilterId,
            Instant now
    ) {
        this.label = nextLabel;
        this.weight = clampedWeight(this.weight + weightDelta);
        this.positiveCount += weightDelta > 0 ? 1 : 0;
        this.negativeCount += weightDelta < 0 ? 1 : 0;
        this.lastBehavior = behavior;
        if (this.suggestedFilterId == null) {
            this.suggestedFilterId = nextSuggestedFilterId;
        }
        this.updatedAt = now;
        return this;
    }

    public UserTasteSignal update(Double nextWeight, Boolean disabled, Instant now) {
        if (nextWeight != null) {
            this.weight = clampedWeight(nextWeight);
        }
        if (disabled != null) {
            this.status = disabled ? UserTasteSignalStatus.DISABLED : UserTasteSignalStatus.ACTIVE;
        }
        this.updatedAt = now;
        return this;
    }

    public UserTasteSignal acceptSuggestion(Instant now) {
        this.suggestionStatus = UserTasteSuggestionStatus.ACCEPTED;
        this.updatedAt = now;
        return this;
    }

    public UserTasteSignal rejectSuggestion(Instant now) {
        this.suggestionStatus = UserTasteSuggestionStatus.REJECTED;
        this.updatedAt = now;
        return this;
    }

    private static double clampedWeight(double value) {
        return Math.max(-10.0d, Math.min(10.0d, value));
    }
}
