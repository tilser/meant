package com.meant.api.module.checkout.repository;

import com.meant.api.module.checkout.entity.CheckoutPurchaseAttribution;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckoutPurchaseAttributionRepository
        extends JpaRepository<CheckoutPurchaseAttribution, UUID> {

    boolean existsByUserIdAndCheckoutAttemptId(UUID userId, UUID checkoutAttemptId);

    @Modifying
    @Query(value = """
            INSERT INTO checkout_purchase_attribution (
                id, user_id, cart_id, checkout_attempt_id, attribution_rail, attribution_trigger,
                embedded_session_id, purchased_at, created_at
            ) VALUES (
                :id, :userId, :cartId, :checkoutAttemptId, :rail, :trigger,
                :embeddedSessionId, :purchasedAt, :createdAt
            )
            ON CONFLICT (user_id, checkout_attempt_id) DO NOTHING
            """, nativeQuery = true)
    int reserve(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("cartId") UUID cartId,
            @Param("checkoutAttemptId") UUID checkoutAttemptId,
            @Param("rail") String rail,
            @Param("trigger") String trigger,
            @Param("embeddedSessionId") UUID embeddedSessionId,
            @Param("purchasedAt") Instant purchasedAt,
            @Param("createdAt") Instant createdAt
    );
}
