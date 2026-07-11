package com.meant.api.module.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Retention-safe saved interaction containing identifiers and provenance, never provider payload. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "user_saved_products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_saved_products_user_product",
                        columnNames = {"user_id", "product_key"}
                )
        }
)
public class UserSavedProduct {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String productKey;

    private String sourceProvider;

    private String sourceType;

    private String sourceIdentity;

    private UUID localMerchantId;

    private UUID merchantIntegrationId;

    private String externalMerchantId;

    private String externalProductId;

    private String externalVariantId;

    private String selectedOptionsJson;

    private String retentionPolicyKey;

    private Instant referenceVerifiedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static UserSavedProduct create(
            UUID userId,
            String productKey,
            DurableReferenceSnapshot reference,
            Instant now
    ) {
        return UserSavedProduct.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .productKey(productKey)
                .createdAt(now)
                .updatedAt(now)
                .build()
                .replaceReference(reference, now);
    }

    public UserSavedProduct replaceReference(DurableReferenceSnapshot reference, Instant now) {
        this.sourceProvider = reference.sourceProvider();
        this.sourceType = reference.sourceType();
        this.sourceIdentity = reference.sourceIdentity();
        this.localMerchantId = reference.localMerchantId();
        this.merchantIntegrationId = reference.merchantIntegrationId();
        this.externalMerchantId = reference.externalMerchantId();
        this.externalProductId = reference.externalProductId();
        this.externalVariantId = reference.externalVariantId();
        this.selectedOptionsJson = reference.selectedOptionsJson();
        this.retentionPolicyKey = reference.retentionPolicyKey();
        this.referenceVerifiedAt = now;
        this.updatedAt = now;
        return this;
    }

    public record DurableReferenceSnapshot(
            String sourceProvider,
            String sourceType,
            String sourceIdentity,
            UUID localMerchantId,
            UUID merchantIntegrationId,
            String externalMerchantId,
            String externalProductId,
            String externalVariantId,
            String selectedOptionsJson,
            String retentionPolicyKey
    ) {
    }
}
