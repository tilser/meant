package com.meant.api.module.user.entity;

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

/** Durable identifiers-only anchor for reopening one historical canonical-product offer. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_canonical_product_references")
public class UserCanonicalProductReference extends AssignedIdEntity<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String canonicalProductKey;

    @Column(nullable = false)
    private String offerKey;

    @Column(nullable = false)
    private int offerRank;

    @Column(nullable = false)
    private String sourceProvider;

    @Column(nullable = false)
    private String sourceType;

    @Column(nullable = false)
    private String sourceIdentity;

    private UUID localMerchantId;

    private UUID merchantIntegrationId;

    private String externalMerchantId;

    private String externalMerchantDomain;

    @Column(nullable = false)
    private String externalProductId;

    private String externalVariantId;

    @Column(nullable = false)
    private String selectedOptionsJson;

    @Column(nullable = false)
    private String componentsJson;

    private String sellingPlanJson;

    @Column(nullable = false)
    private String retentionPolicyKey;

    @Column(nullable = false)
    private Instant referenceVerifiedAt;

    @Column(nullable = false)
    private Instant createdAt;

    public static UserCanonicalProductReference create(
            UUID userId,
            String canonicalProductKey,
            String offerKey,
            int offerRank,
            DurableReferenceSnapshot reference,
            Instant now
    ) {
        return UserCanonicalProductReference.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .canonicalProductKey(canonicalProductKey)
                .offerKey(offerKey)
                .offerRank(offerRank)
                .sourceProvider(reference.sourceProvider())
                .sourceType(reference.sourceType())
                .sourceIdentity(reference.sourceIdentity())
                .localMerchantId(reference.localMerchantId())
                .merchantIntegrationId(reference.merchantIntegrationId())
                .externalMerchantId(reference.externalMerchantId())
                .externalMerchantDomain(reference.externalMerchantDomain())
                .externalProductId(reference.externalProductId())
                .externalVariantId(reference.externalVariantId())
                .selectedOptionsJson(reference.selectedOptionsJson())
                .componentsJson(reference.componentsJson())
                .sellingPlanJson(reference.sellingPlanJson())
                .retentionPolicyKey(reference.retentionPolicyKey())
                .referenceVerifiedAt(now)
                .createdAt(now)
                .build();
    }

    public record DurableReferenceSnapshot(
            String sourceProvider,
            String sourceType,
            String sourceIdentity,
            UUID localMerchantId,
            UUID merchantIntegrationId,
            String externalMerchantId,
            String externalMerchantDomain,
            String externalProductId,
            String externalVariantId,
            String selectedOptionsJson,
            String componentsJson,
            String sellingPlanJson,
            String retentionPolicyKey
    ) {
    }
}
