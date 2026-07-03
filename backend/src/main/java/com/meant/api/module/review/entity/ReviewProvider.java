package com.meant.api.module.review.entity;

import com.meant.api.module.review.constant.ReviewProductIdType;
import com.meant.api.module.review.constant.ReviewProviderStatus;
import com.meant.api.module.review.constant.ReviewProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
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
        name = "review_provider",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_review_provider_merchant", columnNames = "merchant_id")
        }
)
public class ReviewProvider {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String merchantDomain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewProviderType provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewProviderStatus status;

    private String providerKey;

    @Enumerated(EnumType.STRING)
    private ReviewProductIdType productIdType;

    private String sourceUrl;

    private String evidence;

    private Instant lastCheckedAt;

    private Instant nextCheckAt;

    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public void markDetected(
            String merchantDomain,
            ReviewProviderType provider,
            String providerKey,
            ReviewProductIdType productIdType,
            String sourceUrl,
            String evidence,
            Instant checkedAt
    ) {
        this.merchantDomain = merchantDomain;
        this.provider = provider;
        this.status = ReviewProviderStatus.DETECTED;
        this.providerKey = providerKey;
        this.productIdType = productIdType;
        this.sourceUrl = sourceUrl;
        this.evidence = evidence;
        this.lastCheckedAt = checkedAt;
        this.nextCheckAt = null;
        this.errorMessage = null;
        this.updatedAt = checkedAt;
    }

    public void markNotFound(String merchantDomain, Instant checkedAt, Instant nextCheckAt) {
        this.merchantDomain = merchantDomain;
        this.provider = ReviewProviderType.NONE;
        this.status = ReviewProviderStatus.NOT_FOUND;
        this.providerKey = null;
        this.productIdType = null;
        this.sourceUrl = null;
        this.evidence = null;
        this.lastCheckedAt = checkedAt;
        this.nextCheckAt = nextCheckAt;
        this.errorMessage = null;
        this.updatedAt = checkedAt;
    }

    public void markRetryableFailure(String merchantDomain, String errorMessage, Instant checkedAt, Instant nextCheckAt) {
        this.merchantDomain = merchantDomain;
        this.provider = ReviewProviderType.UNKNOWN;
        this.status = ReviewProviderStatus.FAILED_RETRYABLE;
        this.providerKey = null;
        this.productIdType = ReviewProductIdType.UNKNOWN;
        this.sourceUrl = null;
        this.evidence = null;
        this.lastCheckedAt = checkedAt;
        this.nextCheckAt = nextCheckAt;
        this.errorMessage = errorMessage;
        this.updatedAt = checkedAt;
    }
}
