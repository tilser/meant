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

/** Saved interaction containing server-resolved identifiers and a non-authoritative presentation snapshot. */
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

    private String productHash;

    private String name;

    private String brand;

    private String category;

    private String tone;

    private String imageUrl;

    private String productUrl;

    private Boolean remote;

    private Integer matchScore;

    private Integer merchantCount;

    private String satisfies;

    private String misses;

    private String note;

    private String pros;

    private String cons;

    private Double reviewScore;

    private Integer reviewCount;

    private String reviewInsight;

    private String needs;

    private String provides;

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
            PresentationSnapshot presentation,
            Instant now
    ) {
        return UserSavedProduct.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .productKey(productKey)
                .createdAt(now)
                .updatedAt(now)
                .build()
                .replace(reference, presentation, now);
    }

    public UserSavedProduct replace(
            DurableReferenceSnapshot reference,
            PresentationSnapshot presentation,
            Instant now
    ) {
        replaceReference(reference, now);
        return replacePresentation(presentation, now);
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

    public UserSavedProduct replacePresentation(PresentationSnapshot snapshot, Instant now) {
        this.productHash = snapshot.productHash();
        this.name = snapshot.name();
        this.brand = snapshot.brand();
        this.category = snapshot.category();
        this.tone = snapshot.tone();
        this.imageUrl = snapshot.imageUrl();
        this.productUrl = snapshot.productUrl();
        this.remote = snapshot.remote();
        this.matchScore = snapshot.matchScore();
        this.merchantCount = snapshot.merchantCount();
        this.satisfies = snapshot.satisfies();
        this.misses = snapshot.misses();
        this.note = snapshot.note();
        this.pros = snapshot.pros();
        this.cons = snapshot.cons();
        this.reviewScore = snapshot.reviewScore();
        this.reviewCount = snapshot.reviewCount();
        this.reviewInsight = snapshot.reviewInsight();
        this.needs = snapshot.needs();
        this.provides = snapshot.provides();
        this.updatedAt = now;
        return this;
    }

    public record PresentationSnapshot(
            String productHash,
            String name,
            String brand,
            String category,
            String tone,
            String imageUrl,
            String productUrl,
            Boolean remote,
            Integer matchScore,
            Integer merchantCount,
            String satisfies,
            String misses,
            String note,
            String pros,
            String cons,
            Double reviewScore,
            Integer reviewCount,
            String reviewInsight,
            String needs,
            String provides
    ) {
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
