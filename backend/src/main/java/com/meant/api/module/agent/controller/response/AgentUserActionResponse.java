package com.meant.api.module.agent.controller.response;

import com.meant.api.module.agent.service.AgentBuyerPayloadSanitizer;
import com.meant.api.module.agent.service.dto.AgentUserActionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Persisted direct action result and immutable artifacts.")
public record AgentUserActionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AgentMessageResponse message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String resultJson,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AgentArtifactResponse> artifacts
) {

    public static AgentUserActionResponse from(AgentUserActionResult result) {
        return new AgentUserActionResponse(
                AgentMessageResponse.from(result.message()),
                AgentBuyerPayloadSanitizer.sanitize(result.resultJson()),
                result.artifacts().stream().map(AgentArtifactResponse::from).toList()
        );
    }
}
