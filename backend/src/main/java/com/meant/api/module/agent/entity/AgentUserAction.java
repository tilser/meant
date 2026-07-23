package com.meant.api.module.agent.entity;

import com.meant.api.common.entity.AssignedIdEntity;
import com.meant.api.module.agent.constant.AgentMutationAdmission;
import com.meant.api.module.agent.constant.AgentUserActionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "agent_user_action")
public class AgentUserAction extends AssignedIdEntity<UUID> {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID conversationId;

    @Column(nullable = false, updatable = false)
    private String toolName;

    @Column(nullable = false, updatable = false)
    private String toolVersion;

    @Column(nullable = false, updatable = false)
    private String argumentsJson;

    @Column(nullable = false, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentUserActionStatus status;

    private UUID messageId;

    private String resultJson;

    private String safeMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    public void start(Instant now) {
        status = AgentUserActionStatus.RUNNING;
        startedAt = now;
    }

    public void retry() {
        if (status != AgentUserActionStatus.UNCERTAIN && !retryableAdmissionFailure()) {
            throw new IllegalStateException(
                    "Only an uncertain action or mutation rejected before admission can be retried");
        }
        status = AgentUserActionStatus.RESERVED;
        messageId = null;
        resultJson = null;
        safeMessage = null;
        startedAt = null;
        completedAt = null;
    }

    public boolean retryableAdmissionFailure() {
        return status == AgentUserActionStatus.FAILED
                && AgentMutationAdmission.USER_ACTION_SAFE_MESSAGE.equals(safeMessage);
    }

    public void complete(UUID resultingMessageId, String result, Instant now) {
        status = AgentUserActionStatus.COMPLETED;
        messageId = resultingMessageId;
        resultJson = result;
        completedAt = now;
    }

    public void fail(AgentUserActionStatus terminalStatus, String message, Instant now) {
        if (terminalStatus != AgentUserActionStatus.FAILED
                && terminalStatus != AgentUserActionStatus.UNCERTAIN) {
            throw new IllegalArgumentException("User action failure status must be terminal");
        }
        status = terminalStatus;
        safeMessage = message;
        completedAt = now;
    }
}
