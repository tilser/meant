package com.meant.api.module.user.entity;

import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
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
@Table(name = "user_product_search_qualifications")
public class UserProductSearchQualification {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(updatable = false)
    private UUID merchantId;

    @Column(nullable = false, updatable = false)
    private String originalQuery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserProductSearchQualificationStatus status;

    @Column(nullable = false)
    private String planJson;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String promptVersion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static UserProductSearchQualification create(
            UUID id,
            UUID userId,
            UUID conversationId,
            UUID merchantId,
            String originalQuery,
            UserProductSearchQualificationStatus status,
            String planJson,
            String model,
            String promptVersion,
            Instant now
    ) {
        return UserProductSearchQualification.builder()
                .id(id)
                .userId(userId)
                .conversationId(conversationId)
                .merchantId(merchantId)
                .originalQuery(originalQuery)
                .status(status)
                .planJson(planJson)
                .model(model)
                .promptVersion(promptVersion)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void updatePending(
            UserProductSearchQualificationStatus nextStatus,
            String nextPlanJson,
            String nextModel,
            String nextPromptVersion,
            Instant now
    ) {
        if (status == UserProductSearchQualificationStatus.READY) {
            throw new IllegalStateException("A ready product-search qualification is immutable");
        }
        status = Objects.requireNonNull(nextStatus, "Qualification status is required");
        planJson = Objects.requireNonNull(nextPlanJson, "Qualification plan is required");
        model = Objects.requireNonNull(nextModel, "Qualification model is required");
        promptVersion = Objects.requireNonNull(nextPromptVersion, "Qualification prompt version is required");
        updatedAt = Objects.requireNonNull(now, "Qualification update time is required");
    }

    /** Extends the short execution window without changing an immutable READY plan. */
    public void refreshReady(Instant now) {
        if (status != UserProductSearchQualificationStatus.READY) {
            throw new IllegalStateException("Only a ready product-search qualification can be refreshed");
        }
        updatedAt = Objects.requireNonNull(now, "Qualification refresh time is required");
    }

}
