package com.meant.api.module.checkout.entity;

import com.meant.api.common.entity.AssignedIdEntity;
import com.meant.api.module.checkout.constant.EmbeddedCheckoutSessionStatus;
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
@Table(name = "embedded_checkout_session")
public class EmbeddedCheckoutSession extends AssignedIdEntity<UUID> {
    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID cartId;

    @Column(nullable = false, updatable = false)
    private String checkoutId;

    @Column(nullable = false, updatable = false)
    private UUID checkoutAttemptId;

    @Column(updatable = false)
    private UUID merchantIntegrationId;

    @Column(nullable = false, updatable = false)
    private String routingScopeKey;

    @Column(nullable = false, updatable = false)
    private String allowedOrigin;

    @Column(nullable = false, updatable = false)
    private String protocolVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmbeddedCheckoutSessionStatus status;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    private Instant cancelledAt;

    private Instant openedAt;

    public void complete(Instant now) {
        requireCompletable(now);
        status = EmbeddedCheckoutSessionStatus.COMPLETED;
        completedAt = now;
        cancelledAt = null;
    }

    public void cancel(Instant now) {
        requireActive(now);
        status = EmbeddedCheckoutSessionStatus.CANCELLED;
        cancelledAt = now;
    }

    public void requireActive(Instant now) {
        if (status != EmbeddedCheckoutSessionStatus.ACTIVE || !expiresAt.isAfter(now)) {
            throw new IllegalStateException("Embedded checkout session is no longer active");
        }
    }

    public void requireCompletable(Instant now) {
        boolean cancelledAfterStart = status == EmbeddedCheckoutSessionStatus.CANCELLED && openedAt != null;
        if ((status != EmbeddedCheckoutSessionStatus.ACTIVE && !cancelledAfterStart)
                || !expiresAt.isAfter(now)) {
            throw new IllegalStateException("Embedded checkout session is no longer active");
        }
    }

    public void acknowledgeOpened(Instant now) {
        if (openedAt != null) {
            return;
        }
        if (!expiresAt.isAfter(now)) {
            throw new IllegalStateException("Embedded checkout session is expired");
        }
        openedAt = now;
    }
}
