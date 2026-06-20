package com.meant.api.module.merchant.entity;

import com.meant.api.module.merchant.constant.MerchantIdentityLinkStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "merchant_identity_link",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_merchant_identity_link_user_merchant",
                        columnNames = {"user_id", "merchant_id"}
                ),
                @UniqueConstraint(name = "uk_merchant_identity_link_state_hash", columnNames = "state_hash")
        }
)
public class MerchantIdentityLink {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantIdentityLinkStatus status;

    @Column(nullable = false)
    private String stateHash;

    @Column(nullable = false)
    private String codeVerifierCiphertext;

    private String issuer;

    private String accessTokenCiphertext;

    private String refreshTokenCiphertext;

    private String tokenType;

    private String scope;

    private Instant expiresAt;

    private Instant lastRefreshedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public void startAuthorization(
            Merchant merchant,
            String stateHash,
            String codeVerifierCiphertext,
            String issuer,
            Instant updatedAt
    ) {
        this.merchant = merchant;
        this.status = MerchantIdentityLinkStatus.PENDING;
        this.stateHash = stateHash;
        this.codeVerifierCiphertext = codeVerifierCiphertext;
        this.issuer = issuer;
        this.accessTokenCiphertext = null;
        this.refreshTokenCiphertext = null;
        this.tokenType = null;
        this.scope = null;
        this.expiresAt = null;
        this.lastRefreshedAt = null;
        this.updatedAt = updatedAt;
    }

    public void connect(
            String accessTokenCiphertext,
            String refreshTokenCiphertext,
            String tokenType,
            String scope,
            String stateHash,
            String codeVerifierCiphertext,
            Instant expiresAt,
            Instant updatedAt
    ) {
        this.status = MerchantIdentityLinkStatus.CONNECTED;
        this.stateHash = stateHash;
        this.codeVerifierCiphertext = codeVerifierCiphertext;
        this.accessTokenCiphertext = accessTokenCiphertext;
        this.refreshTokenCiphertext = refreshTokenCiphertext;
        this.tokenType = tokenType;
        this.scope = scope;
        this.expiresAt = expiresAt;
        this.lastRefreshedAt = updatedAt;
        this.updatedAt = updatedAt;
    }

    public void refresh(
            String accessTokenCiphertext,
            String refreshTokenCiphertext,
            String tokenType,
            String scope,
            Instant expiresAt,
            Instant updatedAt
    ) {
        this.accessTokenCiphertext = accessTokenCiphertext;
        this.refreshTokenCiphertext = refreshTokenCiphertext;
        this.tokenType = tokenType;
        this.scope = scope;
        this.expiresAt = expiresAt;
        this.lastRefreshedAt = updatedAt;
        this.updatedAt = updatedAt;
    }
}
