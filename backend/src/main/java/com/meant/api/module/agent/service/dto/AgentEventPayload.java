package com.meant.api.module.agent.service.dto;

import java.util.UUID;

public record AgentEventPayload(
        String text,
        UUID messageId,
        Long sequenceNumber,
        String modelToolCallId,
        String toolName,
        String summary,
        String resultJson,
        String failureCode,
        AgentArtifactResult artifact
) {

    public static AgentEventPayload text(String text) {
        return new AgentEventPayload(text, null, null, null, null, null, null, null, null);
    }

    public static AgentEventPayload assistant(UUID messageId, long sequence, String text) {
        return new AgentEventPayload(text, messageId, sequence, null, null, null, null, null, null);
    }

    public static AgentEventPayload tool(String callId, String toolName, String summary, String resultJson) {
        return new AgentEventPayload(null, null, null, callId, toolName, summary, resultJson, null, null);
    }

    public static AgentEventPayload failure(String code, String summary) {
        return new AgentEventPayload(null, null, null, null, null, summary, null, code, null);
    }

    public static AgentEventPayload artifact(AgentArtifactResult artifact) {
        return new AgentEventPayload(null, null, null, null, null, null, null, null, artifact);
    }
}
