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
                @UniqueConstraint(name = "uk_merchant_raw_dataset_row_idx", columnNames = "dataset_row_idx"),
                @UniqueConstraint(name = "uk_merchant_raw_domain", columnNames = "domain")
        }
)
public class MerchantRaw {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
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
}
