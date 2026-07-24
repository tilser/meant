package com.meant.api.module.agent.controller.response;

import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.service.AgentBuyerPayloadSanitizer;
import com.meant.api.module.agent.service.dto.AgentMessageResult;
import com.meant.api.module.merchant.service.MerchantBuyerTextSanitizer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Immutable agent transcript message.")
public record AgentMessageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID messageId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) UUID runId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sequenceNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AgentMessageRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AgentContentKind contentKind,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String textContent,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String contentJson,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String correlationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt
) {

    public static AgentMessageResponse from(AgentMessageResult result) {
        return new AgentMessageResponse(
                result.messageId(),
                result.runId(),
                result.sequenceNumber(),
                result.role(),
                result.contentKind(),
                MerchantBuyerTextSanitizer.sanitize(result.textContent()),
                AgentBuyerPayloadSanitizer.sanitize(result.contentJson()),
                result.correlationId(),
                result.createdAt()
        );
    }
}
