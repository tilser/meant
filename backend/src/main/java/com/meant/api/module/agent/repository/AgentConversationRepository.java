package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.entity.AgentConversation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, UUID> {

    Optional<AgentConversation> findByIdAndUserId(UUID id, UUID userId);

    List<AgentConversation> findByUserIdAndStatusOrderByUpdatedAtDesc(
            UUID userId,
            AgentConversationStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from AgentConversation conversation where conversation.id = :id and conversation.userId = :userId")
    Optional<AgentConversation> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
