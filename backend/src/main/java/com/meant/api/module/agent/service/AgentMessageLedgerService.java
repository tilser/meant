package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentRunEventType;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.dto.AgentEventPayload;
import com.meant.api.module.agent.service.dto.AgentMessageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentMessageLedgerService {

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRunRepository runRepository;
    private final AgentRunService runService;
    private final AgentConversationMemoryService memoryService;
    private final Clock clock;

    @Transactional
    public AgentMessageResult appendAssistant(UUID runId, String text) {
        return appendAssistantLocked(runId, null, text, null);
    }

    @Transactional
    public AgentMessageResult appendAssistant(UUID runId, UUID executionOwner, String text) {
        return appendAssistantLocked(runId, executionOwner, text, null);
    }

    @Transactional
    public AgentMessageResult appendTerminalAssistant(UUID runId, String text, boolean waitingForUser) {
        AgentMessageResult result = appendAssistantLocked(runId, null, text, null);
        if (waitingForUser) {
            runService.waitForUser(runId, text);
        } else {
            runService.complete(runId);
        }
        memoryService.refreshForRun(runId);
        return result;
    }

    @Transactional
    public AgentMessageResult appendTerminalAssistant(
            UUID runId,
            UUID executionOwner,
            String text,
            boolean waitingForUser
    ) {
        return appendTerminalAssistant(runId, executionOwner, text, null, waitingForUser);
    }

    @Transactional
    public AgentMessageResult appendTerminalAssistant(
            UUID runId,
            UUID executionOwner,
            String text,
            String contentJson,
            boolean waitingForUser
    ) {
        AgentMessageResult result = appendAssistantLocked(runId, executionOwner, text, contentJson);
        if (waitingForUser) {
            runService.waitForUser(runId, executionOwner, text);
        } else {
            runService.complete(runId, executionOwner);
        }
        memoryService.refreshForRun(runId);
        return result;
    }

    private AgentMessageResult appendAssistantLocked(
            UUID runId,
            UUID executionOwner,
            String text,
            String contentJson
    ) {
        AgentRun initial = runRepository.findById(runId).orElseThrow(AgentException::notFound);
        AgentConversation conversation = conversationRepository.findOwnedForUpdate(
                        initial.getConversationId(),
                        initial.getUserId()
                )
                .orElseThrow(AgentException::notFound);
        AgentRun run = runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        if (run.isCancellationRequested()) {
            throw new CancellationException("Agent run cancellation was requested");
        }
        Instant now = clock.instant();
        AgentMessage message = messageRepository.save(AgentMessage.builder()
                .conversationId(conversation.getId())
                .runId(run.getId())
                .role(AgentMessageRole.ASSISTANT)
                .contentKind(AgentContentKind.TEXT)
                .sequenceNumber(conversation.nextSequence(now))
                .textContent(text)
                .contentJson(contentJson)
                .createdAt(now)
                .build());
        AgentMessageResult result = AgentResultMapper.message(message);
        appendEvent(
                runId,
                executionOwner,
                AgentRunEventType.ASSISTANT_COMPLETED,
                AgentEventPayload.assistant(message.getId(), message.getSequenceNumber(), text)
        );
        return result;
    }

    @Transactional
    public AgentMessageResult appendToolResult(
            UUID runId,
            String modelToolCallId,
            String toolName,
            String resultJson
    ) {
        return appendToolResult(runId, null, modelToolCallId, toolName, resultJson);
    }

    @Transactional
    public AgentMessageResult appendToolResult(
            UUID runId,
            UUID executionOwner,
            String modelToolCallId,
            String toolName,
            String resultJson
    ) {
        AgentRun run = runRepository.findById(runId).orElseThrow(AgentException::notFound);
        AgentConversation conversation = conversationRepository.findOwnedForUpdate(
                        run.getConversationId(),
                        run.getUserId()
                )
                .orElseThrow(AgentException::notFound);
        if (executionOwner == null) {
            runRepository.findForUpdate(runId).orElseThrow(AgentException::notFound);
        } else {
            runService.requireOwnedExecution(runId, executionOwner);
        }
        Instant now = clock.instant();
        AgentMessage message = messageRepository.save(AgentMessage.builder()
                .conversationId(conversation.getId())
                .runId(run.getId())
                .role(AgentMessageRole.TOOL)
                .contentKind(AgentContentKind.TOOL_RESULT)
                .sequenceNumber(conversation.nextSequence(now))
                .contentJson(resultJson)
                .correlationId(modelToolCallId + ":" + toolName)
                .createdAt(now)
                .build());
        return AgentResultMapper.message(message);
    }

    private void appendEvent(
            UUID runId,
            UUID executionOwner,
            AgentRunEventType type,
            AgentEventPayload payload
    ) {
        if (executionOwner == null) {
            runService.append(runId, type, payload);
            return;
        }
        runService.append(runId, executionOwner, type, payload);
    }
}
