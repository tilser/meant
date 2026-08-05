package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentRun;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    Optional<AgentRun> findByIdAndUserId(UUID id, UUID userId);

    List<AgentRun> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    Optional<AgentRun> findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    Optional<AgentRun> findByTriggeringMessageId(UUID triggeringMessageId);

    Optional<AgentRun> findFirstByConversationIdAndStatusInOrderByCreatedAtAscIdAsc(
            UUID conversationId,
            Collection<AgentRunStatus> statuses
    );

    boolean existsByConversationIdAndStatusIn(UUID conversationId, Collection<AgentRunStatus> statuses);

    List<AgentRun> findByStatusOrderByIdAsc(AgentRunStatus status, Pageable pageable);

    List<AgentRun> findByStatusAndIdGreaterThanOrderByIdAsc(
            AgentRunStatus status,
            UUID afterId,
            Pageable pageable
    );

    @Query("""
            select run from AgentRun run
            where run.status = :status
              and (run.leaseExpiresAt is null or run.leaseExpiresAt <= :now)
            order by run.leaseExpiresAt asc, run.createdAt asc
            """)
    List<AgentRun> findExpiredLeases(
            @Param("status") AgentRunStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    boolean existsByConversationIdAndStatusAndIdNot(
            UUID conversationId,
            AgentRunStatus status,
            UUID id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from AgentRun run where run.id = :id")
    Optional<AgentRun> findForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from AgentRun run where run.id = :id and run.userId = :userId")
    Optional<AgentRun> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
