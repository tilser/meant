package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.entity.GuestConversationTransfer;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestConversationTransferRepository extends JpaRepository<GuestConversationTransfer, UUID> {

    @Query(value = """
            SELECT 1
            FROM pg_advisory_xact_lock(hashtextextended(CAST(:conversationId AS text), 1))
            """, nativeQuery = true)
    int lockConversationTransfer(@Param("conversationId") UUID conversationId);

    Optional<GuestConversationTransfer> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from GuestConversationTransfer transfer where transfer.conversationId = :conversationId")
    Optional<GuestConversationTransfer> findByConversationIdForUpdate(@Param("conversationId") UUID conversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transfer from GuestConversationTransfer transfer where transfer.tokenHash = :tokenHash")
    Optional<GuestConversationTransfer> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
