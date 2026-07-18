package com.meant.api.module.agent.controller.response;

import com.meant.api.module.agent.service.dto.SubmitAgentTurnResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Accepted turn and queued run identifiers.")
public record SubmitAgentTurnResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID runId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long firstEventCursor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AgentMessageResponse userMessage
) {

    public static SubmitAgentTurnResponse from(SubmitAgentTurnResult result) {
        return new SubmitAgentTurnResponse(
                result.runId(),
                result.firstEventCursor(),
                AgentMessageResponse.from(result.userMessage())
        );
    }
}
