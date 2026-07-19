package com.meant.api.module.agent.service;

import com.meant.api.module.agent.constant.AgentContentKind;
import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.agent.constant.AgentRunStatus;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.entity.AgentRun;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.command.CancelAgentRunCommand;
import com.meant.api.module.agent.service.command.SubmitAgentTurnCommand;
import com.meant.api.module.agent.service.dto.SubmitAgentTurnResult;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class AgentTurnService {

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentRunRepository runRepository;
    private final AgentRunService runService;
    private final AgentVisibleProductContextService visibleProductContextService;
    private final AgentProperties properties;
    private final Clock clock;

    @Transactional
    public SubmitAgentTurnResult submit(@Valid SubmitAgentTurnCommand command) {
        if (!properties.enabled()) {
            throw AgentException.disabled();
        }
        AgentConversation conversation = conversationRepository.findOwnedForUpdate(
                        command.conversationId(),
                        command.userId()
                )
                .orElseThrow(AgentException::notFound);
        if (conversation.getStatus() == AgentConversationStatus.ARCHIVED) {
            throw AgentException.conflict("Archived conversations cannot accept new turns.");
        }
        var visibleProductContext = visibleProductContextService.resolve(
                conversation.getId(), command.visibleProductContext());
        String turnContextJson = visibleProductContextService.serialize(visibleProductContext);

        String clientTurnId = blankToNull(command.clientTurnId());
        if (clientTurnId != null) {
            var existing = messageRepository.findByConversationIdAndRoleAndCorrelationId(
                    conversation.getId(), AgentMessageRole.USER, clientTurnId);
            if (existing.isPresent()) {
                AgentMessage message = existing.get();
                if (!command.message().trim().equals(message.getTextContent())
                        || !java.util.Objects.equals(turnContextJson, message.getContentJson())) {
                    throw AgentException.conflict("This client turn identifier was already used.");
                }
                AgentRun run = runRepository.findByTriggeringMessageId(message.getId())
                        .orElseThrow(AgentException::notFound);
                return new SubmitAgentTurnResult(
                        run.getId(),
                        run.getLastEventCursor(),
                        AgentResultMapper.message(message)
                );
            }
        }

        runRepository.findFirstByConversationIdAndStatusInOrderByCreatedAtAsc(
                        conversation.getId(),
                        List.of(AgentRunStatus.RUNNING)
                )
                .ifPresent(run -> runService.requestCancellation(new CancelAgentRunCommand(
                        command.userId(),
                        run.getId()
                )));
        runRepository.findFirstByConversationIdAndStatusInOrderByCreatedAtAsc(
                        conversation.getId(),
                        List.of(AgentRunStatus.QUEUED)
                )
                .ifPresent(run -> runService.cancel(run.getId()));
        runRepository.flush();

        Instant now = clock.instant();
        String text = command.message().trim();
        if (conversation.getLastSequenceNumber() == 0) {
            conversation.rename(deriveTitle(text), now);
        }
        AgentMessage userMessage = messageRepository.saveAndFlush(AgentMessage.builder()
                .conversationId(conversation.getId())
                .role(AgentMessageRole.USER)
                .contentKind(AgentContentKind.TEXT)
                .sequenceNumber(conversation.nextSequence(now))
                .textContent(text)
                .contentJson(turnContextJson)
                .correlationId(clientTurnId)
                .createdAt(now)
                .build());
        AgentRun run = runRepository.saveAndFlush(AgentRun.queued(
                conversation.getId(),
                command.userId(),
                userMessage.getId(),
                properties.model(),
                properties.promptVersion(),
                command.buyerIp(),
                now
        ));
        userMessage.linkRun(run.getId());
        messageRepository.save(userMessage);
        return new SubmitAgentTurnResult(run.getId(), 0L, AgentResultMapper.message(userMessage));
    }

    private String deriveTitle(String text) {
        String normalized = text.replaceAll("\\s+", " ");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
