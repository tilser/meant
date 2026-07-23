package com.meant.api.module.agent.entity;

import com.meant.api.common.entity.AssignedIdEntity;
import com.meant.api.module.agent.constant.AgentArtifactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "agent_artifact_reference")
public class AgentArtifactReference extends AssignedIdEntity<UUID> {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(updatable = false)
    private UUID messageId;

    @Column(updatable = false)
    private UUID runId;

    @Column(updatable = false)
    private UUID toolInvocationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AgentArtifactType artifactType;

    @Column(nullable = false, updatable = false)
    private int ordinal;

    @Column(nullable = false, updatable = false)
    private String stableKey;

    @Column(updatable = false)
    private String label;

    @Column(updatable = false)
    private String canonicalProductKey;

    @Column(updatable = false)
    private String offerKey;

    @Column(updatable = false)
    private UUID inventoryItemId;

    @Column(updatable = false)
    private UUID cartId;

    @Column(updatable = false)
    private UUID cartLineId;

    @Column(updatable = false)
    private UUID checkoutAttemptId;

    @Column(nullable = false, updatable = false)
    private String payloadJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
