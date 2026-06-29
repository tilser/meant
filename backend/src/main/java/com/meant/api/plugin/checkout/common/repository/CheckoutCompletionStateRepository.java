package com.meant.api.plugin.checkout.common.repository;

import com.meant.api.plugin.checkout.common.entity.CheckoutCompletionState;
import com.meant.api.plugin.checkout.common.entity.CheckoutCompletionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckoutCompletionStateRepository extends JpaRepository<CheckoutCompletionState, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CheckoutCompletionState> findByCheckoutIdHash(String checkoutIdHash);

    @Query("select state from CheckoutCompletionState state where state.checkoutIdHash = :checkoutIdHash")
    Optional<CheckoutCompletionState> findReadOnlyByCheckoutIdHash(@Param("checkoutIdHash") String checkoutIdHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CheckoutCompletionState state
            set state.status = :replacement, state.updatedAt = :updatedAt
            where state.checkoutIdHash = :checkoutIdHash and state.status = :expected
            """)
    int compareAndSetStatus(
            @Param("checkoutIdHash") String checkoutIdHash,
            @Param("expected") CheckoutCompletionStatus expected,
            @Param("replacement") CheckoutCompletionStatus replacement,
            @Param("updatedAt") Instant updatedAt
    );
}
