package com.meant.api.module.discount.entity;

import com.meant.api.module.discount.constant.DiscountCodeStatus;
import com.meant.api.module.merchant.entity.Merchant;
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
        name = "discount_code_candidate",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_discount_code_candidate_search_code", columnNames = {"search_id", "code"})
        }
)
public class DiscountCodeCandidate {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private DiscountCodeSearch search;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Merchant merchant;

    @Column(nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountCodeStatus status;

    private String title;

    private String description;

    private String sourceUrl;

    private Double confidence;

    private String restrictions;

    private String validFromText;

    private String validUntilText;

    private Instant validUntil;

    private Instant validatedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private String validationMessage;

    @Column(nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
