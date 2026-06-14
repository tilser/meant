package com.meant.api.module.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "merchant_capability_requirement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_merchant_capability_requirement_name",
                columnNames = {"merchant_capability_id", "required_capability_name"}
        )
)
public class MerchantCapabilityRequirement {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_capability_id", nullable = false)
    private MerchantCapability merchantCapability;

    @Column(nullable = false)
    private String requiredCapabilityName;

    private String minVersion;

    private String maxVersion;
}
