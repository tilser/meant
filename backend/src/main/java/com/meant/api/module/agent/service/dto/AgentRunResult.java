package com.meant.api.module.agent.service.dto;

import com.meant.api.module.agent.constant.AgentRunStatus;
import java.time.Instant;
import java.util.UUID;

public record AgentRunResult(
        UUID runId,
        UUID conversationId,
        AgentRunStatus status,
        String model,
        String promptVersion,
        int iterationCount,
        int toolInvocationCount,
        Long inputTokens,
        Long outputTokens,
        String failureCode,
        String safeMessage,
        boolean cancellationRequested,
        long latestCursor,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
}
