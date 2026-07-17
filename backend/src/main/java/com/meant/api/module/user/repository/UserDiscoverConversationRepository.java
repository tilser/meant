package com.meant.api.module.user.repository;

import com.meant.api.module.user.entity.UserDiscoverConversation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserDiscoverConversationRepository extends JpaRepository<UserDiscoverConversation, UUID> {

    @Query(
            value = "select pg_advisory_xact_lock(hashtextextended(cast(:id as text), 0))",
            nativeQuery = true
    )
    void lockId(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from UserDiscoverConversation conversation where conversation.id = :id")
    Optional<UserDiscoverConversation> findByIdForUpdate(@Param("id") UUID id);

    Optional<UserDiscoverConversation> findByIdAndUserIdAndKind(
            UUID id,
            UUID userId,
            String kind
    );

    List<UserDiscoverConversation> findByUserIdAndKindOrderByUpdatedAtDesc(
            UUID userId,
            String kind,
            Pageable pageable
    );
}
