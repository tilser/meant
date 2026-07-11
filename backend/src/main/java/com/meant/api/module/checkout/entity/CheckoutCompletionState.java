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
        name = "checkout_completion_state",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_checkout_completion_state_hash", columnNames = "checkout_id_hash")
        }
)
public class CheckoutCompletionState {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    private UUID cartId;

    @Column(nullable = false, updatable = false)
    private String checkoutId;

    @Column(nullable = false, updatable = false)
    private String checkoutIdHash;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CheckoutCompletionStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public void authorize(UUID cartId, Instant updatedAt) {
        this.cartId = cartId;
        this.status = CheckoutCompletionStatus.AUTHORIZED_TO_COMPLETE;
        this.updatedAt = updatedAt;
    }

    public void markCompleted(Instant updatedAt) {
        this.status = CheckoutCompletionStatus.COMPLETED;
        this.updatedAt = updatedAt;
    }

    public void markCanceled(Instant updatedAt) {
        this.status = CheckoutCompletionStatus.CANCELED;
        this.updatedAt = updatedAt;
    }

    public void transitionTo(CheckoutCompletionStatus status, Instant updatedAt) {
        this.status = status;
        this.updatedAt = updatedAt;
    }
}
