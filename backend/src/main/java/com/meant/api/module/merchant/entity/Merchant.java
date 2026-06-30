package com.meant.api.module.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
        name = "merchant",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_merchant_raw_id", columnNames = "merchant_raw_id"),
                @UniqueConstraint(name = "uk_merchant_domain", columnNames = "domain")
        }
)
public class Merchant {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_raw_id", nullable = false)
    private MerchantRaw merchantRaw;

    @Column(nullable = false)
    private String domain;

    @Column(nullable = false)
    private String ucpUrl;

    @Column(nullable = false)
    private String ucpVersion;

    private String advertisedMcpEndpoint;

    private String profileMcpEndpoint;

    @Column(nullable = false)
    private String profileHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String about;

    @Column(nullable = false)
    private String targetAudience;

    @Column(nullable = false)
    private String profileQuestion;

    @Column(nullable = false)
    private String profileAnswerRaw;

    @Column(nullable = false)
    private boolean active;

    @Builder.Default
    @Column(nullable = false)
    private boolean nativeCheckoutEnabled = false;

    @Column(nullable = false)
    private Instant lastProfiledAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public void updateProfile(
            MerchantRaw merchantRaw,
            String domain,
            String ucpUrl,
            String ucpVersion,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint,
            String profileHash,
            String name,
            String description,
            String about,
            String targetAudience,
            String profileQuestion,
            String profileAnswerRaw,
            boolean active,
            Instant lastProfiledAt,
            Instant updatedAt
    ) {
        this.merchantRaw = merchantRaw;
        this.domain = domain;
        this.ucpUrl = ucpUrl;
        this.ucpVersion = ucpVersion;
        this.advertisedMcpEndpoint = advertisedMcpEndpoint;
        this.profileMcpEndpoint = profileMcpEndpoint;
        this.profileHash = profileHash;
        this.name = name;
        this.description = description;
        this.about = about;
        this.targetAudience = targetAudience;
        this.profileQuestion = profileQuestion;
        this.profileAnswerRaw = profileAnswerRaw;
        this.active = active;
        this.lastProfiledAt = lastProfiledAt;
        this.updatedAt = updatedAt;
    }

    public void updateProfileMetadata(
            MerchantRaw merchantRaw,
            String domain,
            String ucpUrl,
            String ucpVersion,
            String advertisedMcpEndpoint,
            String profileMcpEndpoint,
            boolean active,
            Instant lastProfiledAt,
            Instant updatedAt
    ) {
        this.merchantRaw = merchantRaw;
        this.domain = domain;
        this.ucpUrl = ucpUrl;
        this.ucpVersion = ucpVersion;
        this.advertisedMcpEndpoint = advertisedMcpEndpoint;
        this.profileMcpEndpoint = profileMcpEndpoint;
        this.active = active;
        this.lastProfiledAt = lastProfiledAt;
        this.updatedAt = updatedAt;
    }

    public void markInactive(Instant updatedAt) {
        this.active = false;
        this.updatedAt = updatedAt;
    }
}
