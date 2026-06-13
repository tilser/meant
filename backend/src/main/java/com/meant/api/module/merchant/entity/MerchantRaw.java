package com.meant.api.module.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "merchant_raw",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_merchant_raw_dataset_row_idx", columnNames = "dataset_row_idx"),
                @UniqueConstraint(name = "uk_merchant_raw_domain", columnNames = "domain")
        }
)
public class MerchantRaw {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "dataset_row_idx", nullable = false)
    private Integer datasetRowIdx;

    @Column(name = "domain", nullable = false, columnDefinition = "text")
    private String domain;

    @Column(name = "status", nullable = false, columnDefinition = "text")
    private String status;

    @Column(name = "ucp_url", nullable = false, columnDefinition = "text")
    private String ucpUrl;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "ucp_version", columnDefinition = "text")
    private String ucpVersion;

    @Column(name = "has_checkout", nullable = false)
    private boolean hasCheckout;

    @Column(name = "has_identity_linking", nullable = false)
    private boolean hasIdentityLinking;

    @Column(name = "has_cart_management", nullable = false)
    private boolean hasCartManagement;

    @Column(name = "has_order", nullable = false)
    private boolean hasOrder;

    @Column(name = "has_payment_token", nullable = false)
    private boolean hasPaymentToken;

    @Column(name = "capability_count", nullable = false)
    private Integer capabilityCount;

    @Column(name = "ai_bot_policies", nullable = false, columnDefinition = "text")
    private String aiBotPolicies;

    @Column(name = "transports", nullable = false, columnDefinition = "text")
    private String transports;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    protected MerchantRaw() {
    }

    public MerchantRaw(
            Integer datasetRowIdx,
            String domain,
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
            OffsetDateTime lastCheckedAt,
            OffsetDateTime lastSuccessAt,
            OffsetDateTime fetchedAt
    ) {
        this.id = UUID.randomUUID();
        this.datasetRowIdx = datasetRowIdx;
        this.domain = domain;
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
    }

    public Integer getDatasetRowIdx() {
        return datasetRowIdx;
    }

    public String getDomain() {
        return domain;
    }

    public String getStatus() {
        return status;
    }

    public String getAiBotPolicies() {
        return aiBotPolicies;
    }

    public String getTransports() {
        return transports;
    }
}
