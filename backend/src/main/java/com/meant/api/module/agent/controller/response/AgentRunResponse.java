package com.meant.api.module.agent.controller.response;

import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.service.dto.AgentRunResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Durable agent run snapshot.")
public record AgentRunResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID runId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AgentRunStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String model,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String promptVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int iterationCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int toolInvocationCount,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long inputTokens,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long outputTokens,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String failureCode,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String safeMessage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cancellationRequested,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long latestCursor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant startedAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant completedAt
) {

    public static AgentRunResponse from(AgentRunResult result) {
        return new AgentRunResponse(
                result.runId(),
                result.conversationId(),
                result.status(),
                result.model(),
                result.promptVersion(),
                result.iterationCount(),
                result.toolInvocationCount(),
                result.inputTokens(),
                result.outputTokens(),
                result.failureCode(),
                result.safeMessage(),
                result.cancellationRequested(),
                result.latestCursor(),
                result.createdAt(),
                result.startedAt(),
                result.completedAt()
        );
    }
}
