package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.entity.AgentUserAction;
import com.meant.api.module.agent.constant.AgentUserActionStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AgentUserActionRepository extends JpaRepository<AgentUserAction, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AgentUserAction> findByUserIdAndConversationIdAndIdempotencyKey(
            UUID userId,
            UUID conversationId,
            String idempotencyKey
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentUserAction action
            set action.status = :uncertainStatus,
                action.safeMessage = :safeMessage,
                action.completedAt = :now
            where action.status = :runningStatus
              and action.startedAt < :cutoff
            """)
    int markStaleRunningUncertain(
            @Param("runningStatus") AgentUserActionStatus runningStatus,
            @Param("uncertainStatus") AgentUserActionStatus uncertainStatus,
            @Param("safeMessage") String safeMessage,
            @Param("cutoff") Instant cutoff,
            @Param("now") Instant now
    );
}
