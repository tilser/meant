package com.meant.api.module.checkout.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "checkout_idempotency_key",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_checkout_idempotency_key", columnNames = "idempotency_key")
        }
)
public class CheckoutIdempotencyKey {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private String bodyHash;

    @Column(nullable = false, updatable = false)
    private String checkoutId;

    @Column(nullable = false, updatable = false)
    private UUID consentId;

    @Column(nullable = false, updatable = false)
    private Long amount;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false, updatable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckoutIdempotencyStatus status;

    private String remoteResponse;

    private String finalOrderRef;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public void markInFlight(String remoteResponse, Instant updatedAt) {
        this.status = CheckoutIdempotencyStatus.COMPLETION_IN_FLIGHT;
        this.remoteResponse = remoteResponse;
        this.updatedAt = updatedAt;
    }

    public void markCompleted(String remoteResponse, String finalOrderRef, Instant updatedAt) {
        this.status = CheckoutIdempotencyStatus.COMPLETED;
        this.remoteResponse = remoteResponse;
        this.finalOrderRef = finalOrderRef;
        this.updatedAt = updatedAt;
    }

    public void markFailed(String remoteResponse, Instant updatedAt) {
        this.status = CheckoutIdempotencyStatus.FAILED;
        this.remoteResponse = remoteResponse;
        this.updatedAt = updatedAt;
    }
}
