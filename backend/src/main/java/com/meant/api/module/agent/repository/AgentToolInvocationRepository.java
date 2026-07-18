package com.meant.api.module.agent.repository;

import com.meant.api.module.agent.entity.AgentToolInvocation;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AgentToolInvocationRepository extends JpaRepository<AgentToolInvocation, UUID> {

    List<AgentToolInvocation> findByRunIdOrderByCreatedAtAsc(UUID runId);

    Optional<AgentToolInvocation> findByRunIdAndIdempotencyKey(UUID runId, String idempotencyKey);

    Optional<AgentToolInvocation> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invocation from AgentToolInvocation invocation where invocation.idempotencyKey = :idempotencyKey")
    Optional<AgentToolInvocation> findByIdempotencyKeyForUpdate(@Param("idempotencyKey") String idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentToolInvocation invocation
            set invocation.status = :uncertainStatus,
                invocation.failureClassification = :classification,
                invocation.safeMessage = :safeMessage,
                invocation.completedAt = :now
            where invocation.status = :runningStatus
              and invocation.riskClass <> :readRisk
              and invocation.startedAt < :cutoff
            """)
    int markStaleMutationsUncertain(
            @Param("runningStatus") AgentToolInvocationStatus runningStatus,
            @Param("uncertainStatus") AgentToolInvocationStatus uncertainStatus,
            @Param("readRisk") AgentToolRisk readRisk,
            @Param("classification") String classification,
            @Param("safeMessage") String safeMessage,
            @Param("cutoff") Instant cutoff,
            @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AgentToolInvocation invocation
            set invocation.status = :failedStatus,
                invocation.failureClassification = :classification,
                invocation.safeMessage = :safeMessage,
                invocation.completedAt = :now
            where invocation.status = :runningStatus
              and invocation.riskClass = :readRisk
              and invocation.startedAt < :cutoff
            """)
    int markStaleReadsFailed(
            @Param("runningStatus") AgentToolInvocationStatus runningStatus,
            @Param("failedStatus") AgentToolInvocationStatus failedStatus,
            @Param("readRisk") AgentToolRisk readRisk,
            @Param("classification") String classification,
            @Param("safeMessage") String safeMessage,
            @Param("cutoff") Instant cutoff,
            @Param("now") Instant now
    );

    long countByRunIdAndToolName(UUID runId, String toolName);
}
