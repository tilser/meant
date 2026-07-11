package com.meant.api.module.merchant.entity;

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
        name = "merchant_raw",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_merchant_raw_domain", columnNames = "domain")
        }
)
public class MerchantRaw {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    private Integer datasetRowIdx;

    @Column(nullable = false)
    private String domain;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String ucpUrl;

    private Integer httpStatus;

    private String ucpVersion;

    @Column(nullable = false)
    private boolean hasCheckout;

    @Column(nullable = false)
    private boolean hasIdentityLinking;

    @Column(nullable = false)
    private boolean hasCartManagement;

    @Column(nullable = false)
    private boolean hasOrder;

    @Column(nullable = false)
    private boolean hasPaymentToken;

    @Column(nullable = false)
    private Integer capabilityCount;

    private String aiBotPolicies;

    @Column(nullable = false)
    private String transports;

    private Instant lastCheckedAt;

    private Instant lastSuccessAt;

    @Column(nullable = false)
    private Instant fetchedAt;

    @Column(nullable = false)
    private boolean processed;

    private Instant processedAt;

    private String processingStatus;

    private String processingError;

    @Column(nullable = false)
    private String sourceHash;

    @Column(nullable = false)
    private boolean active;

    private Instant lastSeenAt;

    public void updateFromImport(
            Integer datasetRowIdx,
            String status,
            String ucpUrl,
            Integer httpStatus,
            String ucpVersion,
            boolean hasCheckout,
            boolean hasIdentityLinking,
            boolean hasCartManagement,
            boolean hasOrder,
            boolean hasPaymentToken,
            Integer capabilityCount,
            String aiBotPolicies,
            String transports,
            Instant lastCheckedAt,
            Instant lastSuccessAt,
            Instant fetchedAt,
            String sourceHash
    ) {
        boolean sourceChanged = !sourceHash.equals(this.sourceHash);

        this.datasetRowIdx = datasetRowIdx;
        this.status = status;
        this.ucpUrl = ucpUrl;
        this.httpStatus = httpStatus;
        this.ucpVersion = ucpVersion;
        this.hasCheckout = hasCheckout;
        this.hasIdentityLinking = hasIdentityLinking;
        this.hasCartManagement = hasCartManagement;
        this.hasOrder = hasOrder;
        this.hasPaymentToken = hasPaymentToken;
        this.capabilityCount = capabilityCount;
        this.aiBotPolicies = aiBotPolicies;
        this.transports = transports;
        this.lastCheckedAt = lastCheckedAt;
        this.lastSuccessAt = lastSuccessAt;
        this.fetchedAt = fetchedAt;
        this.sourceHash = sourceHash;
        this.active = true;
        this.lastSeenAt = fetchedAt;

        if (sourceChanged) {
            this.processed = false;
            this.processedAt = null;
            this.processingStatus = null;
            this.processingError = null;
        }
    }

    public void markInactive() {
        this.active = false;
        this.processed = false;
        this.processingStatus = "INACTIVE";
        this.processingError = null;
    }

    public void markForEnrichment(Instant observedAt) {
        this.active = true;
        this.processed = false;
        this.processedAt = null;
        this.processingStatus = null;
        this.processingError = null;
        this.lastSeenAt = observedAt;
    }

    public void markProcessed(String processingStatus, Instant processedAt) {
        this.processed = true;
        this.processedAt = processedAt;
        this.processingStatus = processingStatus;
        this.processingError = null;
    }

    public void markProcessingFailure(String processingStatus, String processingError, Instant processedAt) {
        this.processed = false;
        this.processedAt = processedAt;
        this.processingStatus = processingStatus;
        this.processingError = processingError;
    }
}
