package com.meant.api.module.merchant.entity;

import com.meant.api.module.merchant.constant.MerchantIdentityNamespace;
import com.meant.api.module.merchant.constant.MerchantIdentityRole;
import com.meant.api.module.merchant.constant.MerchantRawSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
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
        name = "merchant_identity",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_merchant_identity_claim",
                        columnNames = {"namespace", "normalized_value"}
                )
        }
)
public class MerchantIdentity {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MerchantIdentityNamespace namespace;

    @Column(nullable = false, updatable = false)
    private String normalizedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantIdentityRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MerchantRawSource source;

    @Column(nullable = false, updatable = false)
    private Instant verifiedAt;

}
