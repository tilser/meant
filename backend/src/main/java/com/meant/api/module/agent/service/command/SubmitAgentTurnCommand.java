package com.meant.api.module.agent.service.command;

import com.meant.api.common.util.AcceptLanguageParser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SubmitAgentTurnCommand(
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 8000) String message,
        @Size(max = 120) String clientTurnId,
        @Valid VisibleProductContextCommand visibleProductContext,
        @Valid ShelfContextCommand shelfContext,
        @Size(max = 128) String buyerIp,
        @Size(max = 512) String userAgent,
        @Size(max = AcceptLanguageParser.MAXIMUM_LANGUAGE_TAG_LENGTH) String language
) {

    private static final int MAXIMUM_USER_AGENT_LENGTH = 512;

    public SubmitAgentTurnCommand {
        userAgent = sanitizedUserAgent(userAgent);
        language = AcceptLanguageParser.canonicalLanguageTag(language);
    }

    public SubmitAgentTurnCommand(
            UUID userId,
            UUID conversationId,
            String message,
            String clientTurnId
    ) {
        this(userId, conversationId, message, clientTurnId, null, null, null, null, null);
    }

    public SubmitAgentTurnCommand(
            UUID userId,
            UUID conversationId,
            String message,
            String clientTurnId,
            String buyerIp
    ) {
        this(userId, conversationId, message, clientTurnId, null, null, buyerIp, null, null);
    }

    public SubmitAgentTurnCommand(
            UUID userId,
            UUID conversationId,
            String message,
            String clientTurnId,
            VisibleProductContextCommand visibleProductContext,
            String buyerIp
    ) {
        this(userId, conversationId, message, clientTurnId,
                visibleProductContext, null, buyerIp, null, null);
    }

    public SubmitAgentTurnCommand(
            UUID userId,
            UUID conversationId,
            String message,
            String clientTurnId,
            VisibleProductContextCommand visibleProductContext,
            ShelfContextCommand shelfContext,
            String buyerIp,
            String userAgent
    ) {
        this(userId, conversationId, message, clientTurnId,
                visibleProductContext, shelfContext, buyerIp, userAgent, null);
    }

    private static String sanitizedUserAgent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value.replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.isBlank()) {
            return null;
        }
        return sanitized.length() <= MAXIMUM_USER_AGENT_LENGTH
                ? sanitized
                : sanitized.substring(0, MAXIMUM_USER_AGENT_LENGTH);
    }
}
