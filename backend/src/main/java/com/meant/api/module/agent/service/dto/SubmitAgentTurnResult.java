package com.meant.api.module.agent.service.dto;

import java.util.UUID;

public record SubmitAgentTurnResult(
        UUID runId,
        long firstEventCursor,
        AgentMessageResult userMessage
) {
}
