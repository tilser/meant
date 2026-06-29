package com.meant.api.plugin.checkout.common.repository;

import com.meant.api.plugin.checkout.common.entity.CheckoutIdempotencyKey;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CheckoutIdempotencyKeyRepository extends JpaRepository<CheckoutIdempotencyKey, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CheckoutIdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}
