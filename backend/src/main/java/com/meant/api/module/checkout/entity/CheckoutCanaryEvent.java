package com.meant.api.module.checkout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "checkout_canary_event")
public class CheckoutCanaryEvent {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID merchantId;

    @Column(nullable = false, updatable = false)
    private String checkoutIdHash;

    @Column(nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private CheckoutCanaryOutcome outcome;

    private String remoteStatus;

    private String errorCode;

    @Column(nullable = false, updatable = false)
    private boolean chargeMismatch;

    @Column(nullable = false, updatable = false)
    private boolean nativeCheckoutEnabled;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
