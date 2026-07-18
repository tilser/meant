package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentConversationMemoryService {

    private static final int MAXIMUM_SUMMARY_CHARACTERS = 8_000;
    private static final int MAXIMUM_SUMMARY_LINE_CHARACTERS = 500;

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRunRepository runRepository;
    private final AgentProperties properties;
    private final Clock clock;

    @Transactional
    public void refreshForRun(java.util.UUID runId) {
        AgentRun run = runRepository.findById(runId).orElseThrow(AgentException::notFound);
        AgentConversation conversation = conversationRepository.findOwnedForUpdate(
                        run.getConversationId(), run.getUserId())
                .orElseThrow(AgentException::notFound);
        refresh(conversation);
    }

    public void refresh(AgentConversation conversation) {
        long throughSequence = conversation.getLastSequenceNumber() - properties.contextMessageBudget();
        if (throughSequence <= conversation.getSummaryVersion()) {
            return;
        }
        var messages = messageRepository
                .findByConversationIdAndSequenceNumberGreaterThanAndSequenceNumberLessThanEqualOrderBySequenceNumberAsc(
                        conversation.getId(),
                        conversation.getSummaryVersion(),
                        throughSequence
                );
        StringBuilder summary = new StringBuilder(
                conversation.getRollingSummary() == null ? "" : conversation.getRollingSummary()
        );
        for (AgentMessage message : messages) {
            String line = summaryLine(message);
            if (line == null) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append('\n');
            }
            summary.append(line);
        }
        String bounded = tail(summary.toString(), MAXIMUM_SUMMARY_CHARACTERS);
        conversation.replaceSummary(
                bounded.isBlank() ? null : bounded,
                (int) Math.min(Integer.MAX_VALUE, throughSequence),
                clock.instant()
        );
    }

    private String summaryLine(AgentMessage message) {
        String prefix = switch (message.getRole()) {
            case USER -> "User: ";
            case USER_ACTION -> "Verified user action: ";
            case ASSISTANT -> "Meant: ";
            case TOOL -> "Verified tool: ";
            case SYSTEM_SUMMARY -> null;
        };
        if (prefix == null) {
            return null;
        }
        String content = message.getRole() == AgentMessageRole.TOOL
                ? toolName(message.getCorrelationId()) + " completed."
                : message.getTextContent();
        if (content == null || content.isBlank()) {
            return null;
        }
        return prefix + head(content.trim().replaceAll("\\s+", " "), MAXIMUM_SUMMARY_LINE_CHARACTERS);
    }

    private String toolName(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return "commerce capability";
        }
        int separator = correlationId.indexOf(':');
        return separator < 0 || separator == correlationId.length() - 1
                ? correlationId
                : correlationId.substring(separator + 1);
    }

    private String head(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    private String tail(String value, int maximum) {
        if (value.length() <= maximum) {
            return value;
        }
        return "…\n" + value.substring(value.length() - maximum + 2);
    }
}
