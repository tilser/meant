package com.meant.api.module.agent.entity;

import com.meant.api.common.entity.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Server-owned binding between a product-search qualification and its exact similarity anchor.
 *
 * <p>The canonical key is authoritative. The label is retained only as trusted category context
 * for qualification and must never be used to reconstruct the product reference.</p>
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "agent_similarity_search_qualifications")
public class AgentSimilaritySearchQualification extends AssignedIdEntity<UUID> {

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
    private String canonicalProductKey;

    @Column(updatable = false)
    private UUID inventoryItemId;

    @Column(updatable = false)
    private String anchorLabel;

    @Column(nullable = false, updatable = false)
    private String initialUserText;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
