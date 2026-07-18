package com.meant.api.module.agent.entity;

import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "shopping_mission")
public class ShoppingMission {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShoppingMissionStatus status;

    @Column(nullable = false)
    private String assumptionsJson;

    @Column(nullable = false)
    private String requirementsJson;

    @Column(nullable = false)
    private String constraintsJson;

    @Column(nullable = false)
    private String alternativesJson;

    @Column(nullable = false)
    private String coverageJson;

    @Column(nullable = false)
    private String cartReferencesJson;

    @Column(nullable = false)
    private String checkoutReferencesJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public void update(
            String nextGoal,
            ShoppingMissionStatus nextStatus,
            String assumptions,
            String requirements,
            String constraints,
            String alternatives,
            String coverage,
            String cartReferences,
            String checkoutReferences,
            Instant now
    ) {
        goal = nextGoal;
        status = nextStatus;
        assumptionsJson = assumptions;
        requirementsJson = requirements;
        constraintsJson = constraints;
        alternativesJson = alternatives;
        coverageJson = coverage;
        cartReferencesJson = cartReferences;
        checkoutReferencesJson = checkoutReferences;
        updatedAt = now;
    }
}
