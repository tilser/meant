package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.entity.ShoppingMission;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShoppingMissionRepository extends JpaRepository<ShoppingMission, UUID> {

    Optional<ShoppingMission> findByIdAndUserId(UUID id, UUID userId);

    Optional<ShoppingMission> findFirstByConversationIdAndUserIdOrderByUpdatedAtDesc(
            UUID conversationId,
            UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mission from ShoppingMission mission where mission.id = :id and mission.userId = :userId")
    Optional<ShoppingMission> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
