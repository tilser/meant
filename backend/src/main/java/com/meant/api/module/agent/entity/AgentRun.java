package com.meant.api.module.agent.entity;

import com.meant.api.module.agent.constant.AgentRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "agent_run")
public class AgentRun {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID triggeringMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentRunStatus status;

    @Column(nullable = false, updatable = false)
    private String model;

    @Column(nullable = false, updatable = false)
    private String promptVersion;

    private String buyerIp;

    @Column(nullable = false)
    private int iterationCount;

    @Column(nullable = false)
    private int toolInvocationCount;

    private Long inputTokens;

    private Long outputTokens;

    private String failureCode;

    private String safeMessage;

    @Column(nullable = false)
    private boolean cancellationRequested;

    @Column(nullable = false)
    private long lastEventCursor;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    private UUID executionOwner;

    private Instant leaseExpiresAt;

    private Instant heartbeatAt;

    @Version
    private Long version;

    public static AgentRun queued(
            UUID conversationId,
            UUID userId,
            UUID triggeringMessageId,
            String model,
            String promptVersion,
            Instant now
    ) {
        return queued(conversationId, userId, triggeringMessageId, model, promptVersion, null, now);
    }

    public static AgentRun queued(
            UUID conversationId,
            UUID userId,
            UUID triggeringMessageId,
            String model,
            String promptVersion,
            String buyerIp,
            Instant now
    ) {
        return AgentRun.builder()
                .conversationId(conversationId)
                .userId(userId)
                .triggeringMessageId(triggeringMessageId)
                .status(AgentRunStatus.QUEUED)
                .model(model)
                .promptVersion(promptVersion)
                .buyerIp(buyerIp)
                .createdAt(now)
                .build();
    }

    public void claim(UUID owner, Instant now, Instant leaseExpiry) {
        if (status != AgentRunStatus.QUEUED) {
            throw new IllegalStateException("Only a queued agent run can be claimed");
        }
        status = AgentRunStatus.RUNNING;
        startedAt = now;
        executionOwner = owner;
        heartbeatAt = now;
        leaseExpiresAt = leaseExpiry;
    }

    public boolean renewLease(UUID owner, Instant now, Instant leaseExpiry) {
        if (!leaseOwnedBy(owner, now)) {
            return false;
        }
        heartbeatAt = now;
        leaseExpiresAt = leaseExpiry;
        return true;
    }

    public boolean leaseOwnedBy(UUID owner, Instant now) {
        return status == AgentRunStatus.RUNNING
                && owner != null
                && owner.equals(executionOwner)
                && leaseExpiresAt != null
                && leaseExpiresAt.isAfter(now);
    }

    public boolean leaseActiveAt(Instant now) {
        return status == AgentRunStatus.RUNNING
                && leaseExpiresAt != null
                && leaseExpiresAt.isAfter(now);
    }

    public boolean leaseExpiredAt(Instant now) {
        return status == AgentRunStatus.RUNNING
                && (leaseExpiresAt == null || !leaseExpiresAt.isAfter(now));
    }

    public void requeueAfterLeaseExpiry(Instant now) {
        if (!leaseExpiredAt(now) || cancellationRequested) {
            throw new IllegalStateException("Only an abandoned, non-cancelled agent run can be requeued");
        }
        status = AgentRunStatus.QUEUED;
        failureCode = null;
        safeMessage = null;
        completedAt = null;
        executionOwner = null;
        leaseExpiresAt = null;
        heartbeatAt = null;
    }

    public void requestCancellation() {
        if (!status.terminal()) {
            cancellationRequested = true;
        }
    }

    public void recordIteration() {
        iterationCount += 1;
    }

    public void recordToolInvocation() {
        toolInvocationCount += 1;
    }

    public void addUsage(Long promptTokens, Long completionTokens) {
        if (promptTokens != null) {
            inputTokens = (inputTokens == null ? 0 : inputTokens) + promptTokens;
        }
        if (completionTokens != null) {
            outputTokens = (outputTokens == null ? 0 : outputTokens) + completionTokens;
        }
    }

    public long nextEventCursor() {
        lastEventCursor += 1;
        return lastEventCursor;
    }

    public void complete(Instant now) {
        terminal(AgentRunStatus.COMPLETED, null, null, now);
    }

    public void waitForUser(String message, Instant now) {
        terminal(AgentRunStatus.WAITING_FOR_USER, null, message, now);
    }

    public void fail(String code, String message, Instant now) {
        terminal(AgentRunStatus.FAILED, code, message, now);
    }

    public void cancel(Instant now) {
        terminal(AgentRunStatus.CANCELLED, null, "Run cancelled.", now);
    }

    private void terminal(AgentRunStatus next, String code, String message, Instant now) {
        status = next;
        failureCode = code;
        safeMessage = message;
        completedAt = now;
        executionOwner = null;
        leaseExpiresAt = null;
        heartbeatAt = null;
    }
}
