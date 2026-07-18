package com.meant.api.module.agent.controller.response;

import com.meant.api.module.agent.constant.AgentArtifactType;
import com.meant.api.module.agent.service.dto.AgentArtifactResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Immutable typed artifact referenced by a transcript message.")
public record AgentArtifactResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID artifactId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID messageId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID runId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AgentArtifactType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int ordinal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String stableKey,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String label,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String canonicalProductKey,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String offerKey,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID inventoryItemId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID cartId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID cartLineId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID checkoutAttemptId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String payloadJson,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt
) {

    public static AgentArtifactResponse from(AgentArtifactResult result) {
        return new AgentArtifactResponse(
                result.artifactId(),
                result.messageId(),
                result.runId(),
                result.type(),
                result.ordinal(),
                result.stableKey(),
                result.label(),
                result.canonicalProductKey(),
                result.offerKey(),
                result.inventoryItemId(),
                result.cartId(),
                result.cartLineId(),
                result.checkoutAttemptId(),
                result.payloadJson(),
                result.createdAt()
        );
    }
}
