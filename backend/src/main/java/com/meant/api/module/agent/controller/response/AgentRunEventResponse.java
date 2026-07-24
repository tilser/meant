package com.meant.api.module.agent.controller.response;

import com.meant.api.module.agent.service.AgentBuyerPayloadSanitizer;
import com.meant.api.module.agent.service.dto.AgentRunEventResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Versioned replayable run event envelope.")
public record AgentRunEventResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int schemaVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long cursor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID runId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String payloadJson
) {

    public static AgentRunEventResponse from(AgentRunEventResult result) {
        return new AgentRunEventResponse(
                result.schemaVersion(),
                result.cursor(),
                result.conversationId(),
                result.runId(),
                result.type(),
                result.occurredAt(),
                AgentBuyerPayloadSanitizer.sanitize(result.payloadJson())
        );
    }
}
