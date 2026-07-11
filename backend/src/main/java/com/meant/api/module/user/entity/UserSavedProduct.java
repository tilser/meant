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

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String tone;

    private String imageUrl;

    private String productUrl;

    @Column(nullable = false)
    private boolean remote;

    @Column(nullable = false)
    private int matchScore;

    private Double priceFrom;

    @Column(nullable = false)
    private int merchantCount;

    @Column(nullable = false)
    private String satisfies;

    @Column(nullable = false)
    private String misses;

    @Column(nullable = false)
    private String note;

    @Column(nullable = false)
    private String pros;

    @Column(nullable = false)
    private String cons;

    @Column(nullable = false)
    private double reviewScore;

    @Column(nullable = false)
    private int reviewCount;

    @Column(nullable = false)
    private String reviewInsight;

    private String offers;

    private String needs;

    @Column(nullable = false)
    private String provides;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static UserSavedProduct create(
            UUID userId,
            SavedProductSnapshot snapshot,
            Instant now
    ) {
        return UserSavedProduct.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .productKey(snapshot.productKey())
                .createdAt(now)
                .updatedAt(now)
                .build()
                .replaceSnapshot(snapshot, now);
    }

    public UserSavedProduct replaceSnapshot(
            SavedProductSnapshot snapshot,
            Instant now
    ) {
        this.productHash = snapshot.productHash();
        this.name = snapshot.name();
        this.brand = snapshot.brand();
        this.category = snapshot.category();
        this.tone = snapshot.tone();
        this.imageUrl = snapshot.imageUrl();
        this.productUrl = snapshot.productUrl();
        this.remote = snapshot.remote();
        this.matchScore = snapshot.matchScore();
        this.priceFrom = snapshot.priceFrom();
        this.merchantCount = snapshot.merchantCount();
        this.satisfies = snapshot.satisfies();
        this.misses = snapshot.misses();
        this.note = snapshot.note();
        this.pros = snapshot.pros();
        this.cons = snapshot.cons();
        this.reviewScore = snapshot.reviewScore();
        this.reviewCount = snapshot.reviewCount();
        this.reviewInsight = snapshot.reviewInsight();
        this.offers = snapshot.offers();
        this.needs = snapshot.needs();
        this.provides = snapshot.provides();
        this.updatedAt = now;
        return this;
    }

    public UserSavedProduct replaceReference(DurableReferenceSnapshot snapshot, Instant now) {
        this.sourceProvider = snapshot.sourceProvider();
        this.sourceType = snapshot.sourceType();
        this.sourceIdentity = snapshot.sourceIdentity();
        this.localMerchantId = snapshot.localMerchantId();
        this.merchantIntegrationId = snapshot.merchantIntegrationId();
        this.externalMerchantId = snapshot.externalMerchantId();
        this.externalProductId = snapshot.externalProductId();
        this.externalVariantId = snapshot.externalVariantId();
        this.selectedOptionsJson = snapshot.selectedOptionsJson();
        this.retentionPolicyKey = snapshot.retentionPolicyKey();
        this.imageUrl = null;
        this.productUrl = null;
        this.priceFrom = null;
        this.offers = null;
        this.updatedAt = now;
        return this;
    }

    public record SavedProductSnapshot(
            String productKey,
            String productHash,
            String name,
            String brand,
            String category,
            String tone,
            String imageUrl,
            String productUrl,
            boolean remote,
            int matchScore,
            Double priceFrom,
            int merchantCount,
            String satisfies,
            String misses,
            String note,
            String pros,
            String cons,
            double reviewScore,
            int reviewCount,
            String reviewInsight,
            String offers,
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
