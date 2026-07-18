package com.meant.api.module.checkout.entity;

import com.meant.api.module.checkout.constant.CheckoutAttributionRail;
import com.meant.api.module.checkout.constant.CheckoutAttributionTrigger;
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
        name = "checkout_purchase_attribution",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_checkout_purchase_attribution_attempt",
                columnNames = {"user_id", "checkout_attempt_id"}
        )
)
public class CheckoutPurchaseAttribution {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID cartId;

    @Column(nullable = false, updatable = false)
    private UUID checkoutAttemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CheckoutAttributionRail attributionRail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CheckoutAttributionTrigger attributionTrigger;

    @Column(updatable = false)
    private UUID embeddedSessionId;

    @Column(nullable = false, updatable = false)
    private Instant purchasedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
