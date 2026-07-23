package com.meant.api.module.checkout.entity;

import com.meant.api.common.entity.AssignedIdEntity;
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
        name = "buyer_consent",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_buyer_consent_checkout_terms", columnNames = {
                        "user_id",
                        "merchant_id",
                        "checkout_id_hash",
                        "presented_terms_hash"
                })
        }
)
public class BuyerConsent extends AssignedIdEntity<UUID> {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID merchantId;

    @Column(nullable = false, updatable = false)
    private String checkoutId;

    @Column(nullable = false, updatable = false)
    private String checkoutIdHash;

    @Column(nullable = false, updatable = false)
    private String lineItemsJson;

    @Column(nullable = false, updatable = false)
    private Long totalAmount;

    @Column(nullable = false, updatable = false)
    private String currency;

    private Long taxAmount;

    private String shippingAddressJson;

    private String shippingMethod;

    @Column(nullable = false, updatable = false)
    private String paymentInstrumentHash;

    @Column(nullable = false, updatable = false)
    private String presentedTermsHash;

    @Column(nullable = false, updatable = false)
    private Instant consentedAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
