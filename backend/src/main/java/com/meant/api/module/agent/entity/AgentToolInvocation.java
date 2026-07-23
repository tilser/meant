package com.meant.api.module.agent.entity;

import com.meant.api.module.agent.constant.AgentMutationAdmission;
import com.meant.api.module.agent.constant.AgentToolInvocationStatus;
import com.meant.api.module.agent.constant.AgentToolRisk;
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
@Table(name = "agent_tool_invocation")
public class AgentToolInvocation {

    @Id
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID runId;

    @Column(nullable = false, updatable = false)
    private String modelToolCallId;

    @Column(nullable = false, updatable = false)
    private String toolName;

    @Column(nullable = false, updatable = false)
    private String toolVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AgentToolRisk riskClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentToolInvocationStatus status;

    @Column(nullable = false, updatable = false)
    private String argumentsJson;

    private String resultJson;

    @Column(updatable = false)
    private String idempotencyKey;

    private Long latencyMilliseconds;

    private String failureClassification;

    private String safeMessage;

    private String canonicalProductKey;

    private String offerKey;

    private UUID inventoryItemId;

    private UUID cartId;

    private UUID cartLineId;

    private UUID checkoutAttemptId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    public void start(Instant now) {
        status = AgentToolInvocationStatus.RUNNING;
        startedAt = now;
    }

    public void retry() {
        boolean retryableFailure = status == AgentToolInvocationStatus.FAILED
                && (riskClass == AgentToolRisk.READ
                        || AgentMutationAdmission.FAILURE_CLASSIFICATION.equals(failureClassification));
        if (status != AgentToolInvocationStatus.UNCERTAIN && !retryableFailure) {
            throw new IllegalStateException(
                    "Only an uncertain mutation, failed read, or mutation rejected before admission can be retried");
        }
        status = AgentToolInvocationStatus.PROPOSED;
        resultJson = null;
        latencyMilliseconds = null;
        failureClassification = null;
        safeMessage = null;
        canonicalProductKey = null;
        offerKey = null;
        inventoryItemId = null;
        cartId = null;
        cartLineId = null;
        checkoutAttemptId = null;
        startedAt = null;
        completedAt = null;
    }

    public void complete(
            String result,
            long latency,
            String productKey,
            String selectedOfferKey,
            UUID inventoryId,
            UUID resultingCartId,
            UUID resultingCartLineId,
            UUID resultingCheckoutAttemptId,
            Instant now
    ) {
        status = AgentToolInvocationStatus.COMPLETED;
        resultJson = result;
        latencyMilliseconds = latency;
        canonicalProductKey = productKey;
        offerKey = selectedOfferKey;
        inventoryItemId = inventoryId;
        cartId = resultingCartId;
        cartLineId = resultingCartLineId;
        checkoutAttemptId = resultingCheckoutAttemptId;
        completedAt = now;
    }

    public void fail(AgentToolInvocationStatus terminalStatus, String classification, String message, long latency, Instant now) {
        status = terminalStatus;
        failureClassification = classification;
        safeMessage = message;
        latencyMilliseconds = latency;
        completedAt = now;
    }
}
