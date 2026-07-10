package com.meant.api.module.merchant.entity;

import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A provider connection owned by a merchant. Connection roles are stored separately so one
 * endpoint and authentication strategy can support several commerce operations without duplicating
 * connection or credential identity.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "merchant_integration",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_merchant_integration_connection",
                        columnNames = {"merchant_id", "provider", "endpoint"}
                )
        }
)
public class MerchantIntegration {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantIntegrationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantIntegrationKind kind;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "merchant_integration_role",
            joinColumns = @JoinColumn(name = "merchant_integration_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<MerchantIntegrationRole> roles = new LinkedHashSet<>();

    private String externalMerchantId;

    private String verifiedDomain;

    private String verifiedShopIdentity;

    @Column(nullable = false)
    private String endpoint;

    private String protocolVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantIntegrationAuthStrategy authStrategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantIntegrationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantIntegrationSource source;

    @JdbcTypeCode(SqlTypes.JSON)
    private String rawMetadata;

    @Column(nullable = false)
    private Instant capturedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
