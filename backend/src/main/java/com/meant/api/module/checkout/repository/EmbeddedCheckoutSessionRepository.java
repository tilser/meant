package com.meant.api.module.checkout.repository;

import com.meant.api.module.checkout.entity.EmbeddedCheckoutSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmbeddedCheckoutSessionRepository extends JpaRepository<EmbeddedCheckoutSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from EmbeddedCheckoutSession session where session.id = :id")
    Optional<EmbeddedCheckoutSession> findForUpdate(@Param("id") UUID id);
}
